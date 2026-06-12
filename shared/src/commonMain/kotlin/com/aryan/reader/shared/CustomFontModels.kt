package com.aryan.reader.shared

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class CustomFontItem(
    val id: String,
    val displayName: String,
    val fileName: String,
    val fileExtension: String,
    val path: String,
    val timestamp: Long,
    val isDeleted: Boolean = false
)

data class CustomFontVariantItem(
    val font: CustomFontItem,
    val variant: FontVariant?
)

data class CustomFontFamilyItem(
    val familyName: String,
    val variants: List<CustomFontVariantItem>
)

fun List<CustomFontItem>.groupByFamily(): List<CustomFontFamilyItem> {
    val families = this.groupBy {
        it.displayName.familyFilenameSignature().takeIf { s -> s.isNotBlank() } ?: it.displayName
    }

    return families.map { (familyName, fonts) ->
        val variants = fonts.map { font ->
            CustomFontVariantItem(
                font = font,
                variant = font.displayName.detectFontVariant()
            )
        }
        CustomFontFamilyItem(
            familyName = familyName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            variants = variants
        )
    }.sortedBy { it.familyName }
}

fun CustomFontVariantItem.fontFaceLabel(): String {
    val variant = variant ?: return "Regular"
    return when {
        variant.weight.weight >= FontWeight.Bold.weight && variant.style == FontStyle.Italic -> "Bold Italic"
        variant.weight.weight >= FontWeight.Bold.weight -> "Bold"
        variant.style == FontStyle.Italic -> "Italic"
        variant.weight == FontWeight.Normal -> "Regular"
        variant.weight.weight < FontWeight.Normal.weight -> variant.weight.fontWeightLabel()
        else -> variant.weight.fontWeightLabel()
    }
}

fun CustomFontFamilyItem.fontFaceSummary(): String {
    return variants
        .sortedWith(compareBy<CustomFontVariantItem> {
            it.variant?.style == FontStyle.Italic
        }.thenBy {
            it.variant?.weight?.weight ?: FontWeight.Normal.weight
        })
        .map { it.fontFaceLabel() }
        .distinct()
        .joinToString()
}

fun CustomFontFamilyItem.hasVariableWeightFace(): Boolean {
    return variants.any { variant ->
        variant.font.displayName.supportsVariableWeightAxis() || variant.font.fileName.supportsVariableWeightAxis()
    }
}

private fun FontWeight.fontWeightLabel(): String {
    return when (this) {
        FontWeight.Thin -> "Thin"
        FontWeight.ExtraLight -> "Extra Light"
        FontWeight.Light -> "Light"
        FontWeight.Normal -> "Regular"
        FontWeight.Medium -> "Medium"
        FontWeight.SemiBold -> "Semi Bold"
        FontWeight.Bold -> "Bold"
        FontWeight.ExtraBold -> "Extra Bold"
        FontWeight.Black -> "Black"
        else -> weight.toString()
    }
}
