package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private val supportedEpubFontExtensions = setOf("ttf", "otf", "woff", "woff2")
private val filenameSeparatorsRegex = Regex("""[\s._-]+""")

fun expandFontFacesWithSiblings(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): List<FontFaceInfo> {
    if (fontFaces.isEmpty()) return emptyList()

    val result = fontFaces.toMutableList()
    val existingKeys = result.mapTo(mutableSetOf()) { it.variantKey() }
    val extractionRoot = File(extractionPath)

    fontFaces.forEach { fontFace ->
        val sourceFile = fontFace.resolvedFile(extractionRoot).takeIf { it.isFile } ?: return@forEach
        val sourceSignature = sourceFile.familyFilenameSignature()
        if (sourceSignature.isBlank()) return@forEach
        val parent = sourceFile.parentFile ?: return@forEach

        parent.listFiles()
            ?.asSequence()
            ?.filter { candidate ->
                candidate.isFile &&
                    candidate.extension.lowercase() in supportedEpubFontExtensions &&
                    candidate.familyFilenameSignature() == sourceSignature
            }
            ?.forEach { candidate ->
                val variant = candidate.detectFontVariant() ?: return@forEach
                val src = candidate.toFontFaceSrc(extractionRoot)
                val inferred = fontFace.copy(
                    src = src,
                    fontWeight = variant.weight,
                    fontStyle = variant.style
                )
                if (existingKeys.add(inferred.variantKey())) {
                    result += inferred
                }
            }
    }

    return result
}

fun buildEpubFontFaceCss(
    fontFaces: List<FontFaceInfo>,
    extractionPath: String
): String {
    val extractionRoot = File(extractionPath)
    return expandFontFacesWithSiblings(fontFaces, extractionPath)
        .distinctBy { it.variantKey() }
        .mapNotNull { fontFace ->
            val file = fontFace.resolvedFile(extractionRoot).takeIf { it.isFile } ?: return@mapNotNull null
            val family = fontFace.fontFamily.cssString()
            val url = file.toURI().toString().cssUrlString()
            val weight = fontFace.fontWeight?.weight ?: FontWeight.Normal.weight
            val style = if (fontFace.fontStyle == FontStyle.Italic) "italic" else "normal"
            "@font-face { font-family: '$family'; src: url('$url'); font-weight: $weight; font-style: $style; }"
        }
        .joinToString(separator = " ")
}

private data class FontVariant(
    val weight: FontWeight,
    val style: FontStyle
)

private fun FontFaceInfo.resolvedFile(extractionRoot: File): File {
    val source = File(src)
    return if (source.isAbsolute) source else File(extractionRoot, src)
}

private fun FontFaceInfo.variantKey(): String {
    return listOf(
        fontFamily.trim().lowercase(),
        src.replace('\\', '/').lowercase(),
        fontWeight?.weight ?: FontWeight.Normal.weight,
        fontStyle ?: FontStyle.Normal
    ).joinToString(separator = "|")
}

private fun File.toFontFaceSrc(extractionRoot: File): String {
    val relative = runCatching {
        extractionRoot.toPath().relativize(toPath()).toString()
    }.getOrNull()
    return relative
        ?.takeIf { !it.startsWith("..") && it.isNotBlank() }
        ?.replace(File.separatorChar, '/')
        ?: absolutePath
}

private fun File.familyFilenameSignature(): String {
    val tokens = filenameTokens()
        .filter { token ->
            token.isNotBlank() && token !in fontVariantTokens
        }
    return tokens.joinToString(separator = "")
}

private fun File.detectFontVariant(): FontVariant? {
    val tokens = filenameTokens()
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    val style = if (tokens.any { it in italicTokens || it in compoundItalicTokens }) FontStyle.Italic else FontStyle.Normal
    val weight = tokens.asSequence()
        .mapNotNull { tokenWeightMap[it] ?: compoundTokenWeightMap[it] }
        .maxByOrNull { it.weight }
        ?: FontWeight.Normal

    return FontVariant(weight = weight, style = style)
}

private fun File.filenameTokens(): List<String> {
    return nameWithoutExtension
        .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), "-")
        .lowercase()
        .split(filenameSeparatorsRegex)
}

private val italicTokens = setOf("italic", "ital", "oblique", "obliq", "it")

private val tokenWeightMap = mapOf(
    "thin" to FontWeight.Thin,
    "hairline" to FontWeight.Thin,
    "extralight" to FontWeight.ExtraLight,
    "ultralight" to FontWeight.ExtraLight,
    "light" to FontWeight.Light,
    "regular" to FontWeight.Normal,
    "normal" to FontWeight.Normal,
    "roman" to FontWeight.Normal,
    "book" to FontWeight.Normal,
    "medium" to FontWeight.Medium,
    "semibold" to FontWeight.SemiBold,
    "demibold" to FontWeight.SemiBold,
    "bold" to FontWeight.Bold,
    "extrabold" to FontWeight.ExtraBold,
    "ultrabold" to FontWeight.ExtraBold,
    "black" to FontWeight.Black,
    "heavy" to FontWeight.Black
)

private val compoundItalicTokens = setOf("bolditalic", "boldital", "boldoblique", "boldobliq")
private val compoundTokenWeightMap = compoundItalicTokens.associateWith { FontWeight.Bold }

private val fontVariantTokens = italicTokens + tokenWeightMap.keys +
    compoundItalicTokens

private fun String.cssString(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun String.cssUrlString(): String = replace("\\", "\\\\").replace("'", "%27")
