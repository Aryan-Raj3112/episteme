package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.abs

internal fun SpanStyle.withReaderLinkStyle(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): SpanStyle {
    val linkStyle = readerLinkSpanStyle(
        isDarkTheme = isDarkTheme,
        themeBackgroundColor = themeBackgroundColor,
        themeTextColor = themeTextColor,
        existingDecoration = textDecoration
    )
    return copy(
        color = linkStyle.color,
        background = linkStyle.background,
        textDecoration = linkStyle.textDecoration
    )
}

internal fun readerLinkSpanStyle(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    existingDecoration: TextDecoration? = null
): SpanStyle {
    val background = themeBackgroundColor.takeIf { it.isSpecified }
        ?: if (isDarkTheme) Color.Black else Color.White
    val text = themeTextColor.takeIf { it.isSpecified }
        ?: if (isDarkTheme) Color.White else Color.Black
    val linkColor = readerLinkColorForTheme(isDarkTheme, background, text)
    val backgroundAlpha = if (background.safeLuminance() < 0.45f) 0.24f else 0.16f
    return SpanStyle(
        color = linkColor,
        background = linkColor.copy(alpha = backgroundAlpha),
        textDecoration = existingDecoration.withUnderline()
    )
}

private fun readerLinkColorForTheme(
    isDarkTheme: Boolean,
    background: Color,
    text: Color
): Color {
    val backgroundLuminance = background.safeLuminance()
    val textLuminance = text.safeLuminance()
    val candidates = if (isDarkTheme || backgroundLuminance < 0.45f) {
        listOf(
            Color(0xFF7DD3FC),
            Color(0xFF5EEAD4),
            Color(0xFFA5B4FC),
            Color(0xFFFDE68A),
            Color.White
        )
    } else {
        listOf(
            Color(0xFF005FCC),
            Color(0xFF006D75),
            Color(0xFF7A1E52),
            Color(0xFF4A148C),
            Color(0xFF111827)
        )
    }
    return candidates.firstOrNull {
        it.contrastRatio(background) >= 4.5f && abs(it.safeLuminance() - textLuminance) >= 0.08f
    } ?: candidates.maxByOrNull { it.contrastRatio(background) }
    ?: if (isDarkTheme) Color(0xFF7DD3FC) else Color(0xFF005FCC)
}

private fun TextDecoration?.withUnderline(): TextDecoration {
    val current = this ?: TextDecoration.None
    val decorations = mutableListOf<TextDecoration>()
    if (current.contains(TextDecoration.LineThrough)) decorations += TextDecoration.LineThrough
    decorations += TextDecoration.Underline
    return TextDecoration.combine(decorations)
}

private fun Color.contrastRatio(other: Color): Float {
    val lighter = maxOf(safeLuminance(), other.safeLuminance())
    val darker = minOf(safeLuminance(), other.safeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.safeLuminance(): Float {
    return if (isSpecified) luminance() else 0f
}
