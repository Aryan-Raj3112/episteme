package com.aryan.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.data.CustomFontEntity
import java.io.File

fun AppFontPreference.toAndroidAppFontFamily(customFonts: List<CustomFontEntity>): FontFamily? {
    val sanitized = sanitized()
    return when (sanitized.kind) {
        AppFontPreferenceKind.SYSTEM -> null
        AppFontPreferenceKind.SERIF -> FontFamily.Serif
        AppFontPreferenceKind.SANS_SERIF -> FontFamily.SansSerif
        AppFontPreferenceKind.MONOSPACE -> FontFamily.Monospace
        AppFontPreferenceKind.CUSTOM -> {
            val fontId = sanitized.customFontId ?: return null
            val font = customFonts.firstOrNull { it.id == fontId && !it.isDeleted } ?: return null
            val file = File(font.path).takeIf { it.isFile } ?: return null
            runCatching { FontFamily(Font(file)) }.getOrNull()
        }
    }
}
