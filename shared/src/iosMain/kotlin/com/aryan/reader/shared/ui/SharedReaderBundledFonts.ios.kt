@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.aryan.reader.shared.generated.resources.Res
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

internal actual suspend fun loadSharedReaderBundledFontFamily(fontFamilyName: String): FontFamily? {
    val path = sharedReaderBundledFontFile(fontFamilyName) ?: return null
    val data = runCatching { Res.readBytes(path) }.getOrNull()
        ?.takeIf { it.isNotEmpty() } ?: return null
    // Font(identity, ...) decodes lazily at first layout; an unreadable file
    // must fall back now rather than silently later.
    return runCatching {
        FontFamily(Font(identity = "reader-$fontFamilyName", getData = { data }))
    }.getOrNull()
}

internal actual suspend fun loadSharedReaderCustomFontFamily(fontPath: String): FontFamily? {
    val trimmed = fontPath.trim()
    if (trimmed.isBlank()) return null
    // Same decoder gate as the PDF custom fonts: Skia reliably decodes
    // ttf/otf; woff/woff2 stay unsupported until the native decoder can
    // handle them (Android converts woff2→ttf via Woff2Converter).
    if (trimmed.substringAfterLast('.', "").lowercase() !in setOf("ttf", "otf")) return null
    // Mirrors the settings preview loader (ReaderIosApp fontFamilyForPreview):
    // file bytes straight into a one-variant family. Sibling-weight expansion
    // like Android's `getComposeFontFamily` is a follow-up; a single variant
    // already makes measurement and rendering share the real typeface instead
    // of silently falling back to the system font.
    val data = runCatching {
        NSData.dataWithContentsOfFile(trimmed)?.toByteArray()
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    // Validate now: Font(identity, ...) decodes lazily at first layout, so an
    // unreadable file must fall back here rather than silently later.
    runCatching {
        val skiaData = Data.makeFromBytes(data)
        try {
            (FontMgr.default.makeFromData(skiaData) ?: return null).close()
        } finally {
            skiaData.close()
        }
    }.getOrNull() ?: return null
    return runCatching {
        FontFamily(Font(identity = "reader-custom-$trimmed", getData = { data }))
    }.getOrNull()
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
