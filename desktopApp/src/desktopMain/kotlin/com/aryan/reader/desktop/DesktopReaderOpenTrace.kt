package com.aryan.reader.desktop

internal const val DesktopReaderOpenTraceTag = "EpistemeDesktopOpenTrace"

internal fun logDesktopReaderOpenTrace(message: () -> String) {
    logDesktopDiagnostic(DesktopReaderOpenTraceTag, message)
}

internal fun Long.elapsedOpenTraceMs(nowNanos: Long = System.nanoTime()): Long {
    return ((nowNanos - this).coerceAtLeast(0L)) / 1_000_000L
}

internal fun DesktopReaderOpening.elapsedOpenTraceMs(nowNanos: Long = System.nanoTime()): Long {
    return startedAtNanos.elapsedOpenTraceMs(nowNanos)
}

internal fun DesktopReaderOpening.openTracePrefix(event: String): String {
    return "event=$event requestId=$requestId bookId=\"${bookId.logPreview(80)}\" " +
        "title=\"${title.logPreview(120)}\" format=\"$formatLabel\" elapsedMs=${elapsedOpenTraceMs()}"
}

internal fun DesktopReaderOpenResult.openTraceKind(): String {
    return when (this) {
        is DesktopReaderOpenResult.Failure -> "failure"
        is DesktopReaderOpenResult.PasswordRequired -> "password_required"
        is DesktopReaderOpenResult.Pdf -> "pdf"
        is DesktopReaderOpenResult.Text -> "text"
    }
}
