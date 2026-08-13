package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aryan.reader.shared.detectFontVariant
import com.aryan.reader.shared.familyFilenameSignature

data class EpubFontSiblingCandidate(
    val filename: String,
    val source: String
)

private val SupportedEpubFontExtensions = setOf("ttf", "otf", "woff", "woff2")

fun inferEpubFontFaceSiblings(
    fontFaces: List<FontFaceInfo>,
    candidatesFor: (FontFaceInfo) -> List<EpubFontSiblingCandidate>
): List<FontFaceInfo> {
    if (fontFaces.isEmpty()) return emptyList()
    val result = fontFaces.toMutableList()
    val existingKeys = result.mapTo(mutableSetOf(), FontFaceInfo::epubVariantKey)
    fontFaces.forEach { fontFace ->
        val sourceSignature = fontFace.src.filenameWithoutExtension().familyFilenameSignature()
        if (sourceSignature.isBlank()) return@forEach
        candidatesFor(fontFace).forEach { candidate ->
            if (candidate.filename.extension().lowercase() !in SupportedEpubFontExtensions) return@forEach
            if (candidate.filename.filenameWithoutExtension().familyFilenameSignature() != sourceSignature) return@forEach
            val variant = candidate.filename.filenameWithoutExtension().detectFontVariant() ?: return@forEach
            val inferred = fontFace.copy(
                src = candidate.source,
                fontWeight = variant.weight,
                fontStyle = variant.style
            )
            if (existingKeys.add(inferred.epubVariantKey())) result += inferred
        }
    }
    return result
}

fun FontFaceInfo.epubVariantKey(): String = listOf(
    fontFamily.trim().lowercase(),
    src.replace('\\', '/').lowercase(),
    fontWeight?.weight ?: FontWeight.Normal.weight,
    fontStyle ?: FontStyle.Normal
).joinToString("|")

private fun String.filenameWithoutExtension(): String =
    substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.', missingDelimiterValue = substringAfterLast('/').substringAfterLast('\\'))

private fun String.extension(): String = substringAfterLast('.', missingDelimiterValue = "")
