package com.aryan.reader.shared.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.aryan.reader.shared.generated.resources.Res

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
