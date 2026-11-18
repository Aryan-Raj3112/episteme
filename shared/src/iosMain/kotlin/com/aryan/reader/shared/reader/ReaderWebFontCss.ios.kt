@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.reader

import com.aryan.reader.shared.generated.resources.Res
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

/**
 * iOS WebView font source.
 *
 * The reader document is loaded with `loadHTMLString(html, baseURL = null)`,
 * so WKWebView refuses every `file://` subresource (the previous custom-font
 * CSS pointed at the imported file URL and never loaded). Inlining the face as
 * a data URI gives the WebView bytes it is allowed to use and also covers
 * bundled reader fonts, which were never injected into iOS before.
 *
 * The imported font and the bundled compose resource are immutable app files,
 * so the same path always describes the same face and no invalidation is
 * needed. Font files are at most a few megabytes; the alternative (serving
 * them through a custom URL scheme) adds a CORS contract for no benefit here.
 */
internal actual fun readerWebFontFaceSource(
    fontFamily: String,
    customFontPath: String?,
): String? {
    val customPath = customFontPath?.trim()?.takeIf { it.isNotBlank() }
    return if (customPath != null) {
        readerWebFontSourceCache.getOrPut("custom:$customPath") {
            NSData.dataWithContentsOfFile(customPath)
                ?.takeIf { it.length.toInt() > 0 }
                ?.let { dataUri(it, readerFontMimeType(customPath)) }
        }
    } else {
        val resourcePath = readerBundledFontResourcePath(fontFamily) ?: return null
        readerWebFontSourceCache.getOrPut("bundled:$resourcePath") {
            runCatching {
                val uri = Res.getUri(resourcePath)
                NSURL.URLWithString(uri)?.let { NSData.dataWithContentsOfURL(it) }
                    ?: NSData.dataWithContentsOfFile(uri)
            }.getOrNull()
                ?.takeIf { it.length.toInt() > 0 }
                ?.let { dataUri(it, readerFontMimeType(resourcePath)) }
        }
    }
}

/**
 * The appearance script is rebuilt on every slider step, so a bundled face
 * must not be re-encoded each time. Font files are immutable app assets and
 * imports get a unique path, so the path is a complete cache key.
 */
private object readerWebFontSourceCache {
    private val values = mutableMapOf<String, String>()
    private val lock = NSLock()

    fun getOrPut(key: String, load: () -> String?): String? {
        lock.lock()
        val cached = values[key]
        lock.unlock()
        if (cached != null) return cached
        val value = load() ?: return null
        lock.lock()
        val existing = values[key]
        if (existing == null) values[key] = value
        lock.unlock()
        return existing ?: value
    }
}

private fun dataUri(data: NSData, mimeType: String): String {
    val base64 = data.base64EncodedStringWithOptions(0uL)
    return "url('data:$mimeType;base64,$base64')"
}

private fun readerFontMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    else -> "font/ttf"
}
