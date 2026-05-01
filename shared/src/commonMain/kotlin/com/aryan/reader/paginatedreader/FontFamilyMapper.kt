package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontFamily

/**
 * Shared mapper for generic CSS font-family names.
 *
 * Platform-specific font loaders can still resolve embedded/custom font files and add their own
 * FontFamily instances, but generic CSS families should behave consistently everywhere.
 */
object FontFamilyMapper {
    private val genericFontMap = mapOf(
        "serif" to FontFamily.Serif,
        "sans-serif" to FontFamily.SansSerif,
        "monospace" to FontFamily.Monospace,
        "cursive" to FontFamily.Cursive,
        "default" to FontFamily.Default,
        "system-ui" to FontFamily.Default,
        "ui-sans-serif" to FontFamily.Default,
        "ui-serif" to FontFamily.Default,
        "ui-monospace" to FontFamily.Default,
        "ui-rounded" to FontFamily.Default
    )

    fun nameToFontFamily(name: String): FontFamily? {
        return genericFontMap[name.trim().lowercase()]
    }

    fun fontFamilyToName(fontFamily: FontFamily): String? {
        return when (fontFamily) {
            FontFamily.Serif -> "serif"
            FontFamily.SansSerif -> "sans-serif"
            FontFamily.Monospace -> "monospace"
            FontFamily.Cursive -> "cursive"
            FontFamily.Default -> "default"
            else -> null
        }
    }
}
