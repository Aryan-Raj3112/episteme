package com.aryan.reader.shared

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Font file rules shared by every platform's picker and importer.
 *
 * Pickers filter on MIME/UTI, but providers regularly report a generic stream
 * MIME for `.ttf`/`.otf` files, so the picker advertises the aliases plus that
 * fallback and the importer validates the file extension.
 */
object SharedFontFiles {
    val supportedExtensions: Set<String> = setOf("ttf", "otf", "woff2")

    val androidPickerMimeTypes: List<String> = listOf(
        "font/*",
        "font/ttf",
        "font/otf",
        "font/collection",
        "font/woff",
        "font/woff2",
        "application/x-font-ttf",
        "application/x-font-truetype",
        "application/x-font-otf",
        "application/x-font-opentype",
        "application/x-font-woff",
        "application/x-font-woff2",
        "application/vnd.ms-opentype",
        "application/font-sfnt",
        "application/font-woff",
        "application/font-woff2",
        // Document providers (Drive, cloud folders, some OEM file managers)
        // report unknown extensions as a generic stream. Unsupported files are
        // still rejected by extension after selection.
        "application/octet-stream",
    ).distinct()

    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    fun isSupportedFileName(fileName: String): Boolean = extensionOf(fileName) in supportedExtensions
}

/**
 * Result of one Google Fonts download attempt. Pickers surface [errorMessage]
 * inline; a null message means the font was imported successfully.
 */
data class SharedGoogleFontsDownloadOutcome(val errorMessage: String? = null) {
    val isSuccess: Boolean get() = errorMessage == null
}

/**
 * Bundled Google Fonts catalog entries are the exact `family` value the Google
 * Fonts CSS endpoint expects. A `:face` suffix disambiguates families that do
 * not ship a Regular 400 face (for example `Buda:300` or `Molle:400italic`).
 */
fun googleFontFamilyName(catalogEntry: String): String = catalogEntry.substringBefore(':')

fun googleFontCatalogEntry(familyName: String, catalog: List<String>): String =
    catalog.firstOrNull { googleFontFamilyName(it).equals(familyName, ignoreCase = true) }
        ?: familyName

private val googleFontCssUrlRegex = Regex("""url\((https://[^)]+)\)""")

/** First font URL in a Google Fonts CSS response. */
fun googleFontDownloadUrlFromCss(css: String): String? =
    googleFontCssUrlRegex.find(css)?.groupValues?.getOrNull(1)

/** Lowercase file extension of a Google Fonts download URL. */
fun googleFontDownloadExtension(fontUrl: String): String =
    fontUrl.substringBefore('?').substringAfterLast('.', "ttf").lowercase()

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
