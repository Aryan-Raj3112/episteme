package com.aryan.reader.desktop

private const val DesktopTtsLogTag = "EpistemeDesktopTts"

internal fun logDesktopTts(message: String) {
    println("$DesktopTtsLogTag $message")
}

internal fun Throwable.desktopTtsSummary(): String {
    val type = this::class.java.simpleName.ifBlank { "Throwable" }
    return "$type: ${message.orEmpty().desktopTtsPreview(220)}"
}

internal fun String.desktopTtsPreview(maxLength: Int = 120): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}
