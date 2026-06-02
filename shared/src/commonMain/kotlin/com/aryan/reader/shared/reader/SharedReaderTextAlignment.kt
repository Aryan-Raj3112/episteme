package com.aryan.reader.shared.reader

import androidx.compose.ui.text.style.TextAlign

internal fun resolveSharedReaderTextAlign(
    cssTextAlign: TextAlign,
    fallbackTextAlign: TextAlign
): TextAlign {
    return when {
        fallbackTextAlign.isExplicitSharedReaderTextAlign() -> fallbackTextAlign
        cssTextAlign == TextAlign.Unspecified -> fallbackTextAlign
        cssTextAlign == TextAlign.Justify -> TextAlign.Left
        else -> cssTextAlign
    }
}

private fun TextAlign.isExplicitSharedReaderTextAlign(): Boolean {
    return this != TextAlign.Start &&
        this != TextAlign.Left &&
        this != TextAlign.Unspecified
}

internal fun resolveSharedReaderFontFeatureSettings(
    existingSettings: String?,
    fontVariantNumeric: String?
): String? {
    val existing = existingSettings
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val numericFeatures = fontVariantNumeric
        ?.lowercase()
        ?.split(Regex("\\s+"))
        ?.mapNotNull { token ->
            when (token) {
                "lining-nums" -> "lnum"
                "oldstyle-nums" -> "onum"
                "proportional-nums" -> "pnum"
                "tabular-nums" -> "tnum"
                "diagonal-fractions" -> "frac"
                "stacked-fractions" -> "afrc"
                "ordinal" -> "ordn"
                "slashed-zero" -> "zero"
                else -> null
            }
        }
        ?.distinct()
        ?.map { feature -> "\"$feature\" on" }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    return listOfNotNull(existing, numericFeatures)
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
}
