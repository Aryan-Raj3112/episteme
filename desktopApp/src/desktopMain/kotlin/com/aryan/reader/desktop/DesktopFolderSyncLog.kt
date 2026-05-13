package com.aryan.reader.desktop

private const val DesktopFolderSyncLogTag = "EpistemeFolderSync"

internal fun logDesktopFolderSync(message: String) {
    logDesktopDiagnostic(DesktopFolderSyncLogTag) { message }
}

internal fun Throwable.folderSyncSummary(): String {
    val type = this::class.java.simpleName.ifBlank { "Throwable" }
    return "$type: ${message.orEmpty().folderSyncPreview(220)}"
}

internal fun String.folderSyncPreview(maxLength: Int = 160): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}
