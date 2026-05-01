package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color

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

data class FormatSettings(
    val fontSize: Float,
    val lineHeight: Float,
    val paragraphGap: Float,
    val imageSize: Float,
    val horizontalMargin: Float,
    val font: ReaderFont,
    val customPath: String?,
    val textAlign: ReaderTextAlign
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
