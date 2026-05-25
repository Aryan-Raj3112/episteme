package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReaderFont(val id: String, val displayName: String, val fontFamilyName: String) {
    ORIGINAL("original", "Original", "Original"),
    MERRIWEATHER("merriweather", "Merriweather", "Merriweather"),
    LATO("lato", "Lato", "Lato"),
    LORA("lora", "Lora", "Lora"),
    ROBOTO_MONO("roboto_mono", "Roboto Mono", "Roboto Mono"),
    LEXEND("lexend", "Lexend", "Lexend")
}

enum class ReaderTextAlign(val id: String, val cssValue: String, val displayName: String) {
    DEFAULT("default", "", "Default"),
    LEFT("left", "left", "Left"),
    RIGHT("right", "right", "Right"),
    JUSTIFY("justify", "justify", "Justify")
}

enum class SystemUiMode(val id: Int, val title: String) {
    DEFAULT(0, "Always Show"),
    SYNC(1, "Sync with Menus"),
    HIDDEN(2, "Always Hide")
}

enum class PageInfoMode(val id: Int, val title: String) {
    DEFAULT(0, "Always Show"),
    SYNC(1, "Sync with Menus"),
    HIDDEN(2, "Always Hide")
}

enum class PageInfoPosition(val id: Int, val title: String) {
    BOTTOM(0, "Bottom"),
    TOP(1, "Top")
}

data class FormatSettings(
    val fontSize: Float,
    val lineHeight: Float,
    val paragraphGap: Float,
    val imageSize: Float,
    val horizontalMargin: Float,
    val font: ReaderFont,
    val customPath: String?,
    val textAlign: ReaderTextAlign,
    val verticalMargin: Float = 1.0f
)

enum class ReaderTexture(val id: String, val displayName: String, val assetPath: String) {
    NATURAL_WHITE("asset:ep_naturalwhite.webp", "Natural White", "textures/ep_naturalwhite.webp"),
    NATURAL_BLACK("asset:ep_naturalblack.webp", "Natural Black", "textures/ep_naturalblack.webp"),
    LIGHT_VENEER("asset:light-veneer.webp", "Light Veneer", "textures/light-veneer.webp"),
    RETINA_WOOD("asset:retina_wood.webp", "Retina Wood", "textures/retina_wood.webp"),
    GREY_WASH("asset:grey_wash_wall.webp", "Grey Wash", "textures/grey_wash_wall.webp"),
    CLASSY_FABRIC("asset:classy_fabric.webp", "Classy Fabric", "textures/classy_fabric.webp"),
    RETRO_INTRO("asset:retro_intro.webp", "Retro Intro", "textures/retro_intro.webp"),
    PAPER("paper", "Paper", "textures/texture_paper.png"),
    CANVAS("canvas", "Canvas", "textures/texture_canvas.png"),
    EINK("eink", "E-Ink", "textures/texture_eink.webp"),
    SLATE("slate", "Slate", "textures/texture_slate.png")
}

const val ReaderTextureFilePrefix = "file:"

val ReaderTextureImportExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

fun normalizeReaderTextureExtension(extension: String?): String? {
    val normalized = extension?.trim()?.lowercase() ?: return null
    return when (normalized) {
        "jpeg", "jpg" -> "jpg"
        "png", "webp", "gif", "bmp" -> normalized
        else -> null
    }
}

fun readerTextureMimeTypeForExtension(extension: String): String {
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
}

data class ReaderTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val textColor: Color,
    val isDark: Boolean,
    val textureId: String? = null,
    val isCustom: Boolean = false
)

fun List<ReaderTheme>.sanitizeCustomReaderThemes(): List<ReaderTheme> {
    val seenIds = mutableSetOf<String>()
    return asReversed()
        .filter { theme ->
            theme.isCustom &&
                theme.id.isNotBlank() &&
                theme.name.isNotBlank() &&
                theme.backgroundColor.isSpecified &&
                theme.textColor.isSpecified &&
                seenIds.add(theme.id)
        }
        .asReversed()
}

private val StandardReaderSolidThemes = listOf(
    ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false),
    ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true),
    ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false),
    ReaderTheme("slate", "Slate", Color(0xFF2E3440), Color(0xFFECEFF4), true),
    ReaderTheme("oled", "OLED", Color(0xFF000000), Color(0xFFB0B0B0), true)
)

private val StandardReaderTexturedThemes = listOf(
    ReaderTheme("natural_white_texture", "Natural White", Color(0xFFF7F1E5), Color(0xFF1D1B18), false, textureId = ReaderTexture.NATURAL_WHITE.id),
    ReaderTheme("retina_texture", "Retina", Color(0xFFF1E4CD), Color(0xFF2A2119), false, textureId = ReaderTexture.RETINA_WOOD.id),
    ReaderTheme("veneer_texture", "Veneer", Color(0xFFF4E7CF), Color(0xFF2A2119), false, textureId = ReaderTexture.LIGHT_VENEER.id),
    ReaderTheme("grey_wash_texture", "Grey Wash", Color(0xFF202124), Color(0xFFFFFFFF), true, textureId = ReaderTexture.GREY_WASH.id),
    ReaderTheme("fabric_texture", "Fabric", Color(0xFF262626), Color(0xFFE8E2D8), true, textureId = ReaderTexture.CLASSY_FABRIC.id),
    ReaderTheme("retro_texture", "Retro", Color(0xFFF6ECD8), Color(0xFF2F2118), false, textureId = ReaderTexture.RETRO_INTRO.id)
)

val BuiltInReaderThemes = listOf(
    ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, false)
) + StandardReaderSolidThemes + StandardReaderTexturedThemes

val BuiltInPdfReaderThemes = listOf(
    ReaderTheme("no_theme", "No Theme", Color.Unspecified, Color.Unspecified, false),
    ReaderTheme("reverse", "Reverse", Color.Black, Color.White, true)
) + StandardReaderSolidThemes + StandardReaderTexturedThemes.map { theme ->
    theme.copy(id = "pdf_${theme.id}")
}

fun FormatSettings.toReaderSettings(base: ReaderSettings = ReaderSettings()): ReaderSettings {
    val horizontalMarginPx = (ReaderAppearanceDefaults.marginPx * horizontalMargin).roundToInt()
        .coerceIn(ReaderAppearanceDefaults.minMarginPx, ReaderAppearanceDefaults.maxMarginPx)
    val verticalMarginPx = (ReaderAppearanceDefaults.marginPx * verticalMargin).roundToInt()
        .coerceIn(ReaderAppearanceDefaults.minMarginPx, ReaderAppearanceDefaults.maxMarginPx)
    return base.copy(
        fontSize = (ReaderAppearanceDefaults.fontSizePx * fontSize).roundToInt()
            .coerceIn(ReaderAppearanceDefaults.minFontSizePx, ReaderAppearanceDefaults.maxFontSizePx),
        lineSpacing = (ReaderAppearanceDefaults.lineSpacing * lineHeight)
            .coerceIn(ReaderAppearanceDefaults.minLineSpacing, ReaderAppearanceDefaults.maxLineSpacing),
        margin = max(horizontalMarginPx, verticalMarginPx),
        horizontalMargin = horizontalMarginPx,
        verticalMargin = verticalMarginPx,
        textAlign = textAlign.toSharedReaderTextAlign(),
        fontFamily = customPath?.takeIf { it.isNotBlank() } ?: font.toReaderSettingsFontFamily(),
        customFontPath = customPath?.takeIf { it.isNotBlank() },
        paragraphSpacing = paragraphGap.coerceIn(
            ReaderAppearanceDefaults.minParagraphSpacing,
            ReaderAppearanceDefaults.maxParagraphSpacing
        ),
        imageScale = imageSize.coerceIn(
            ReaderAppearanceDefaults.minImageScale,
            ReaderAppearanceDefaults.maxImageScale
        )
    )
}

fun ReaderTheme.toReaderSettings(base: ReaderSettings = ReaderSettings()): ReaderSettings {
    return base.copy(
        darkMode = isDark,
        themeId = id,
        textureId = textureId,
        backgroundColorArgb = backgroundColor.takeIf { it.isSpecified }?.toArgb()?.toLong(),
        textColorArgb = textColor.takeIf { it.isSpecified }?.toArgb()?.toLong()
    )
}

fun ReaderSettings.resetReaderFormatSettings(): ReaderSettings {
    val defaults = ReaderSettings()
    return copy(
        fontSize = defaults.fontSize,
        lineSpacing = defaults.lineSpacing,
        margin = defaults.margin,
        horizontalMargin = defaults.horizontalMargin,
        verticalMargin = defaults.verticalMargin,
        textAlign = defaults.textAlign,
        pageWidth = defaults.pageWidth,
        fontFamily = defaults.fontFamily,
        paragraphSpacing = defaults.paragraphSpacing,
        imageScale = defaults.imageScale,
        customFontPath = defaults.customFontPath
    )
}

fun ReaderSettings.withHorizontalReaderMargin(horizontalMarginPx: Int): ReaderSettings {
    val nextHorizontal = horizontalMarginPx.coerceIn(
        ReaderAppearanceDefaults.minMarginPx,
        ReaderAppearanceDefaults.maxMarginPx
    )
    val currentVertical = resolvedVerticalMargin.coerceIn(
        ReaderAppearanceDefaults.minMarginPx,
        ReaderAppearanceDefaults.maxMarginPx
    )
    return copy(
        margin = max(nextHorizontal, currentVertical),
        horizontalMargin = nextHorizontal,
        verticalMargin = currentVertical
    )
}

fun ReaderSettings.withVerticalReaderMargin(verticalMarginPx: Int): ReaderSettings {
    val currentHorizontal = resolvedHorizontalMargin.coerceIn(
        ReaderAppearanceDefaults.minMarginPx,
        ReaderAppearanceDefaults.maxMarginPx
    )
    val nextVertical = verticalMarginPx.coerceIn(
        ReaderAppearanceDefaults.minMarginPx,
        ReaderAppearanceDefaults.maxMarginPx
    )
    return copy(
        margin = max(currentHorizontal, nextVertical),
        horizontalMargin = currentHorizontal,
        verticalMargin = nextVertical
    )
}

fun ReaderSettings.shouldShowPageWidthFormatControl(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED
}

fun readerThemeById(themeId: String?): ReaderTheme? {
    return BuiltInReaderThemes.firstOrNull { it.id == themeId }
}

fun readerTextureDisplayName(textureId: String?): String {
    return if (textureId == null) {
        "None"
    } else {
        ReaderTexture.entries.firstOrNull { it.id == textureId }?.displayName
            ?: textureId
                .removePrefix(ReaderTextureFilePrefix)
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .let { fileName -> fileName.substringBeforeLast('.', missingDelimiterValue = fileName) }
                .ifBlank { "Custom Image" }
    }
}

fun RenderMode.toReaderReadingMode(): ReaderReadingMode {
    return when (this) {
        RenderMode.VERTICAL_SCROLL -> ReaderReadingMode.VERTICAL
        RenderMode.PAGINATED -> ReaderReadingMode.PAGINATED
    }
}

fun ReaderTextAlign.toSharedReaderTextAlign(): SharedReaderTextAlign {
    return when (this) {
        ReaderTextAlign.DEFAULT,
        ReaderTextAlign.LEFT -> SharedReaderTextAlign.START
        ReaderTextAlign.RIGHT -> SharedReaderTextAlign.RIGHT
        ReaderTextAlign.JUSTIFY -> SharedReaderTextAlign.JUSTIFY
    }
}

fun ReaderFont.toReaderSettingsFontFamily(): String {
    return when (this) {
        ReaderFont.ORIGINAL -> "Default"
        ReaderFont.MERRIWEATHER,
        ReaderFont.LORA -> "Serif"
        ReaderFont.LATO,
        ReaderFont.LEXEND -> "Sans"
        ReaderFont.ROBOTO_MONO -> "Mono"
    }
}

private object ReaderAppearanceDefaults {
    const val fontSizePx = 18f
    const val minFontSizePx = 12
    const val maxFontSizePx = 42
    const val lineSpacing = 1.45f
    const val minLineSpacing = 1.0f
    const val maxLineSpacing = 2.8f
    const val marginPx = 48f
    const val minMarginPx = 0
    const val maxMarginPx = 160
    const val minParagraphSpacing = 0.5f
    const val maxParagraphSpacing = 2.5f
    const val minImageScale = 0.5f
    const val maxImageScale = 2.0f
}
