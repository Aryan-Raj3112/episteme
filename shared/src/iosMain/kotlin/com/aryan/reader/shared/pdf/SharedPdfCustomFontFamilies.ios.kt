@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.aryan.reader.shared.CustomFontItem
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

internal actual fun loadSharedPdfCustomFontFamilies(
    customFonts: List<CustomFontItem>,
): Map<String, FontFamily> {
    return customFonts
        .asSequence()
        .filterNot(CustomFontItem::isDeleted)
        // Skia's mobile font loader accepts the same local formats that the
        // PDF exporter can rasterize.  WOFF/WOFF2 are retained in the library
        // for EPUB/web use, but are not offered here until native Skia gains a
        // reliable decoder for them.
        .filter { it.fileExtension.lowercase() in setOf("ttf", "otf") }
        .mapNotNull { font ->
            val data = NSData.dataWithContentsOfFile(font.path)?.toByteArray()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val family = runCatching {
                // Font(identity, ...) defers decoding until the first text
                // layout. Validate the bytes now so an unreadable/corrupt
                // path never appears as a selectable font and falls back later.
                val skiaData = Data.makeFromBytes(data)
                val typeface = try {
                    FontMgr.default.makeFromData(skiaData)
                } finally {
                    skiaData.close()
                } ?: return@runCatching null
                typeface.close()
                FontFamily(
                    Font(
                        identity = "pdf-imported-${font.id}",
                        getData = { data },
                    )
                )
            }.getOrNull() ?: return@mapNotNull null
            listOf(font.id, font.path, font.displayName).map { key -> key to family }
        }
        .flatten()
        .toMap()
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
