package com.aryan.reader.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font as DesktopFont
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.AppFontPreferenceKind
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import java.io.File

internal fun ReaderSettings.toDesktopReaderFontFamily(): FontFamily {
    customFontPath?.takeIf { it.isNotBlank() }?.let { path ->
        runCatching { FontFamily(DesktopFont(File(path))) }.getOrNull()?.let { return it }
    }
    return fontFamily.toComposeFontFamily()
}

private fun String.toComposeFontFamily(): FontFamily {
    return when (this) {
        "Serif" -> FontFamily.Serif
        "Sans" -> FontFamily.SansSerif
        "Mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

internal fun List<ReaderPage>.samePageLayoutAs(other: List<ReaderPage>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        val left = this[index]
        val right = other[index]
        left.pageIndex == right.pageIndex &&
            left.chapterIndex == right.chapterIndex &&
            left.startOffset == right.startOffset &&
            left.endOffset == right.endOffset &&
            left.text.length == right.text.length &&
            left.semanticBlocks == right.semanticBlocks
    }
}

internal fun CustomFontItem.toDesktopPreviewFontFamily(): FontFamily? {
    val file = File(path).takeIf { it.isFile } ?: return null
    return runCatching { FontFamily(DesktopFont(file)) }.getOrNull()
}

internal fun AppFontPreference.toDesktopAppFontFamily(customFonts: List<CustomFontItem>): FontFamily? {
    val sanitized = sanitized()
    return when (sanitized.kind) {
        AppFontPreferenceKind.SYSTEM -> null
        AppFontPreferenceKind.SERIF -> FontFamily.Serif
        AppFontPreferenceKind.SANS_SERIF -> FontFamily.SansSerif
        AppFontPreferenceKind.MONOSPACE -> FontFamily.Monospace
        AppFontPreferenceKind.CUSTOM -> {
            val fontId = sanitized.customFontId ?: return null
            customFonts.firstOrNull { it.id == fontId && !it.isDeleted }
                ?.toDesktopPreviewFontFamily()
        }
    }
}
