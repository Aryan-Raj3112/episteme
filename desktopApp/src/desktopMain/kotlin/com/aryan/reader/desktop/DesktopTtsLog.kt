package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderTtsChunk

private const val DesktopTtsLogTag = "EpistemeDesktopTts"
private const val DesktopTtsStartTraceLogTag = "EpistemeDesktopTtsStartTrace"
private val DesktopTtsSensitiveQueryRegex = Regex("""(?i)([?&](?:key|token)=)[^&\s"]+""")
private val DesktopTtsSensitiveLabelRegex = Regex(
    """(?i)\b((?:geminiKey|groqKey|api[_-]?key|authorization|token)\s*[:=]\s*)[^\s,;"]+"""
)

internal fun logDesktopTts(message: String) {
    logDesktopDiagnostic(DesktopTtsLogTag) { message }
}

internal fun logDesktopTtsStartTrace(message: () -> String) {
    logDesktopDiagnostic(DesktopTtsStartTraceLogTag, message)
}

internal fun ReaderTtsChunk?.desktopTtsStartTraceSummary(maxTextLength: Int = 120): String {
    if (this == null) return "null"
    return "index=$index page=${pageIndex + 1} chapter=$chapterIndex " +
        "offsets=$startOffset..$endOffset sourceCfi=\"${sourceCfi.orEmpty().logPreview(160)}\" " +
        "textChars=${text.length} spokenChars=${spokenText.length} " +
        "text=\"${text.logPreview(maxTextLength)}\" spoken=\"${spokenText.logPreview(maxTextLength)}\""
}

internal fun Throwable.desktopTtsSummary(): String {
    val type = this::class.java.simpleName.ifBlank { "Throwable" }
    return "$type: ${message.orEmpty().desktopTtsPreview(220)}"
}

internal fun String.desktopTtsPreview(maxLength: Int = 120): String {
    return replace(DesktopTtsSensitiveQueryRegex) { match -> match.groupValues[1] + "<redacted>" }
        .replace(DesktopTtsSensitiveLabelRegex) { match -> match.groupValues[1] + "<redacted>" }
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}
