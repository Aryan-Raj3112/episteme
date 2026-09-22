package com.aryan.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.shared.toBaselineAppFontFamily
import java.io.File

fun AppFontPreference.toAndroidAppFontFamily(customFonts: List<CustomFontEntity>): FontFamily? {
    val sanitized = sanitized()
    if (sanitized.kind != AppFontPreferenceKind.CUSTOM) {
        return sanitized.toBaselineAppFontFamily()
    }
    val fontId = sanitized.customFontId ?: return null
    val font = customFonts.firstOrNull { it.id == fontId && !it.isDeleted } ?: return null
    val file = File(font.path).takeIf { it.isFile } ?: return null
    return runCatching { FontFamily(Font(file)) }.getOrNull()
}
