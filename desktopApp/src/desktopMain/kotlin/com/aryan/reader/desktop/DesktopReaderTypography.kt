package com.aryan.reader.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font as DesktopFont
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.AppFontPreferenceKind
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.detectFontVariant
import com.aryan.reader.shared.familyFilenameSignature
import com.aryan.reader.shared.supportsVariableWeightAxis
import java.io.File

internal fun ReaderSettings.toDesktopReaderFontFamily(): FontFamily {
    customFontPath?.takeIf { it.isNotBlank() }?.let { path ->
        val baseFile = File(path)
        val signature = baseFile.nameWithoutExtension.familyFilenameSignature()
        val siblings = baseFile.parentFile?.listFiles()?.filter {
            it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "woff", "woff2") &&
                it.nameWithoutExtension.familyFilenameSignature() == signature
        } ?: listOf(baseFile)

        val seenVariants = mutableSetOf<String>()
        val fontList = siblings.flatMap { sibling ->
            try {
                val variant = sibling.nameWithoutExtension.detectFontVariant()
                val weights = if (sibling.nameWithoutExtension.supportsVariableWeightAxis()) {
                    variableDesktopReaderFontWeights
                } else {
                    listOf(variant?.weight ?: androidx.compose.ui.text.font.FontWeight.Normal)
                }
                weights.mapNotNull { weight ->
                    val style = variant?.style ?: androidx.compose.ui.text.font.FontStyle.Normal
                    if (seenVariants.add("${weight.weight}|$style")) {
                        DesktopFont(sibling, weight, style)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        if (fontList.isNotEmpty()) {
            return FontFamily(fontList)
        }
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

private val variableDesktopReaderFontWeights = listOf(
    androidx.compose.ui.text.font.FontWeight.Thin,
    androidx.compose.ui.text.font.FontWeight.ExtraLight,
    androidx.compose.ui.text.font.FontWeight.Light,
    androidx.compose.ui.text.font.FontWeight.Normal,
    androidx.compose.ui.text.font.FontWeight.Medium,
    androidx.compose.ui.text.font.FontWeight.SemiBold,
    androidx.compose.ui.text.font.FontWeight.Bold,
    androidx.compose.ui.text.font.FontWeight.ExtraBold,
    androidx.compose.ui.text.font.FontWeight.Black
)

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
