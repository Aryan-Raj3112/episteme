package com.aryan.reader.shared.reader

/** The desktop reader has no file-backed reader face; keep the system stack. */
internal actual fun readerWebFontFaceSource(
    fontFamily: String,
    customFontPath: String?,
): String? {
    val path = customFontPath?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return "url('${path.toCssFontUrl()}')"
}
