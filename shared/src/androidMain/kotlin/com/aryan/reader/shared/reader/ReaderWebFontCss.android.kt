package com.aryan.reader.shared.reader

/**
 * Android's WebView loads chapter HTML with a file base URL and file access
 * enabled (ChapterWebView), so the imported font path works as-is. The shared
 * WebView is not used by the Android app; bundled faces stay on the system
 * fallback mapping here rather than being inlined.
 */
internal actual fun readerWebFontFaceSource(
    fontFamily: String,
    customFontPath: String?,
): String? {
    val path = customFontPath?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return "url('${path.toCssFontUrl()}')"
}
