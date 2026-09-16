@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.AppFontPreferenceKind
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.toBaselineAppFontFamily
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * iOS app-text font resolver (Android `AppFontResolver` benchmark).
 *
 * Built-in kinds share [toBaselineAppFontFamily] so both mobile platforms
 * resolve SYSTEM/SERIF/SANS/MONO identically. CUSTOM loads the imported file
 * via NSData, mirroring the custom-fonts preview path; missing/unreadable
 * files fall back to the system font (null).
 */
fun AppFontPreference.toIosAppFontFamily(customFonts: List<CustomFontItem>): FontFamily? {
    val sanitized = sanitized()
    if (sanitized.kind != AppFontPreferenceKind.CUSTOM) {
        return sanitized.toBaselineAppFontFamily()
    }
    val fontId = sanitized.customFontId ?: return null
    val font = customFonts.firstOrNull { it.id == fontId && !it.isDeleted } ?: return null
    val bytes = NSData.dataWithContentsOfFile(font.path)?.toIosByteArray()
        ?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        FontFamily(
            Font(
                identity = "app-font-${font.id}",
                getData = { bytes },
            )
        )
    }.getOrNull()
}

private fun NSData.toIosByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
