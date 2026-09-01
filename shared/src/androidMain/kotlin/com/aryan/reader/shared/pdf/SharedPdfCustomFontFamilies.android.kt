package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.shared.CustomFontItem
import java.io.File

internal actual fun loadSharedPdfCustomFontFamilies(
    customFonts: List<CustomFontItem>,
): Map<String, FontFamily> {
    return customFonts
        .asSequence()
        .filterNot(CustomFontItem::isDeleted)
        .filter { it.fileExtension.lowercase() in setOf("ttf", "otf") }
        .mapNotNull { font ->
            val family = runCatching {
                val file = File(font.path)
                if (!file.isFile) return@runCatching null
                FontFamily(Font(file))
            }.getOrNull() ?: return@mapNotNull null
            listOf(font.id, font.path, font.displayName).map { key -> key to family }
        }
        .flatten()
        .toMap()
}
