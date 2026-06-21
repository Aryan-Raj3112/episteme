package com.aryan.reader.shared

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class FontVariant(
    val weight: FontWeight,
    val style: FontStyle
)

private const val MAX_FONT_FILENAME_TOKEN_CHARS = 512

fun String.familyFilenameSignature(): String {
    val tokens = filenameTokens()
    val variantIndexes = tokens.variantTokenIndexes()
    val signatureTokens = tokens.filterIndexed { index, token ->
        token.isNotBlank() && index !in variantIndexes
    }
    return signatureTokens.joinToString(separator = " ")
}

fun String.supportsVariableWeightAxis(): Boolean {
    return filenameTokens().any { it == "wght" }
}

fun String.fontWeightCssDescriptor(fallbackWeight: FontWeight = FontWeight.Normal): String {
    return if (supportsVariableWeightAxis()) {
        "100 900"
    } else {
        fallbackWeight.weight.toString()
    }
}

private fun List<String>.variantTokenIndexes(): Set<Int> {
    val indexes = mutableSetOf<Int>()
    forEachIndexed { index, token ->
        if (token in singleVariantTokens || token.toIntOrNull()?.isCssFontWeight() == true) {
            indexes += index
        }
        val next = getOrNull(index + 1) ?: return@forEachIndexed
        if ("$token$next" in compoundVariantTokens) {
            indexes += index
            indexes += index + 1
        }
    }
    return indexes
}

private fun Int.isCssFontWeight(): Boolean = this in 100..900 && this % 100 == 0

private fun List<String>.containsCompoundToken(compoundTokens: Set<String>): Boolean {
    return windowed(size = 2, step = 1, partialWindows = false)
        .any { (first, second) -> "$first$second" in compoundTokens }
}

private fun List<String>.detectedWeight(): FontWeight {
    val compactTokens = buildList {
        addAll(this@detectedWeight)
        this@detectedWeight.windowed(size = 2, step = 1, partialWindows = false)
            .forEach { (first, second) -> add("$first$second") }
    }
    return compactTokens.asSequence()
        .mapNotNull { token ->
            tokenWeightMap[token]
                ?: compoundTokenWeightMap[token]
                ?: token.toIntOrNull()?.takeIf { it.isCssFontWeight() }?.let(::FontWeight)
        }
        .maxByOrNull { it.weight }
        ?: FontWeight.Normal
}

private fun List<String>.detectedStyle(): FontStyle {
    return if (any { it in italicTokens } || containsCompoundToken(compoundItalicTokens)) {
        FontStyle.Italic
    } else {
        FontStyle.Normal
    }
}

fun String.detectFontVariant(): FontVariant? {
    val tokens = filenameTokens()
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    return FontVariant(weight = tokens.detectedWeight(), style = tokens.detectedStyle())
}

private fun String.filenameTokens(): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    val limit = minOf(length, MAX_FONT_FILENAME_TOKEN_CHARS)

    fun flushToken() {
        if (current.isNotEmpty()) {
            tokens += current.toString().lowercase()
            current.clear()
        }
    }

    while (index < limit) {
        if (regionMatches(index, "variablefont", 0, "variablefont".length, ignoreCase = true)) {
            flushToken()
            tokens += "variablefont"
            index += "variablefont".length
            continue
        }

        val char = this[index]
        val previous = current.lastOrNull()
        if (char.isFilenameSeparator()) {
            flushToken()
        } else {
            if (previous != null && previous.isLowerCase() && char.isUpperCase()) {
                flushToken()
            }
            current.append(char)
        }
        index += 1
    }
    flushToken()
    return tokens
}

private fun Char.isFilenameSeparator(): Boolean {
    return isWhitespace() || this == '.' || this == '_' || this == ',' || this == '-'
}

private val italicTokens = setOf("italic", "ital", "oblique", "obliq", "it", "itallic", "italics", "slanted", "slant")

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

private val variableFontTokens = setOf(
    "variablefont",
    "vf",
    "variable",
    "wght",
    "wdth",
    "opsz",
    "slnt",
    "grad",
    "xtra",
    "xopq",
    "yopq",
    "ytlc",
    "ytuc",
    "ytas",
    "ytde"
)

private val compoundItalicTokens = setOf("bolditalic", "boldital", "boldoblique", "boldobliq")
private val compoundWeightTokens = mapOf(
    "extralight" to FontWeight.ExtraLight,
    "ultralight" to FontWeight.ExtraLight,
    "semibold" to FontWeight.SemiBold,
    "demibold" to FontWeight.SemiBold,
    "extrabold" to FontWeight.ExtraBold,
    "ultrabold" to FontWeight.ExtraBold
)
private val compoundVariableFontTokens = setOf("variablefont")
private val compoundTokenWeightMap = compoundItalicTokens.associateWith { FontWeight.Bold } + compoundWeightTokens

private val singleVariantTokens = italicTokens + tokenWeightMap.keys + variableFontTokens
private val compoundVariantTokens = compoundItalicTokens + compoundWeightTokens.keys + compoundVariableFontTokens
