package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
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

enum class ReaderTexture(val id: String, val displayName: String) {
    PAPER("paper", "Paper"),
    CANVAS("canvas", "Canvas"),
    EINK("eink", "E-Ink"),
    SLATE("slate", "Slate")
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

val BuiltInReaderThemes = listOf(
    ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, false),
    ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false),
    ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true),
    ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false),
    ReaderTheme("slate", "Slate", Color(0xFF2E3440), Color(0xFFECEFF4), true),
    ReaderTheme("oled", "OLED", Color(0xFF000000), Color(0xFFB0B0B0), true)
)

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
    return base.copy(darkMode = isDark, themeId = id, textureId = textureId)
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
