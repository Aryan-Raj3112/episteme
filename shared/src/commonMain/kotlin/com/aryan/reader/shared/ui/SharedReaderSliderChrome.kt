package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

private const val SharedSliderMinAccentContrast = 3f
private const val SharedSliderMinContentContrast = 4.5f

/**
 * Page-aware slider chrome.
 *
 * Shared port of the Android benchmark (`readerSliderChromeColors`): the
 * bottom-chrome slider floats over the *page*, not over app chrome, so its
 * arrows/track must contrast with the page background. [contentColor] backs
 * the prev/next arrows; [activeTrackColor]/[thumbColor] keep the brand
 * primary only when it stays visible on the page, otherwise they fall back
 * to the content color.
 */
data class SharedReaderSliderChromeColors(
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
    val thumbColor: Color,
    val contentColor: Color,
)

fun sharedReaderSliderChromeColors(
    pageBackground: Color,
    pageText: Color,
    themePrimary: Color,
): SharedReaderSliderChromeColors {
    val background = if (pageBackground == Color.Unspecified) Color.White else pageBackground
    val fallbackContent = sharedSliderHighContrastColorFor(background)
    val content = (if (pageText == Color.Unspecified) fallbackContent else pageText)
        .takeIf { sharedSliderContrastRatio(it, background) >= SharedSliderMinContentContrast }
        ?: fallbackContent
    val active = (if (themePrimary == Color.Unspecified) content else themePrimary)
        .takeIf { sharedSliderContrastRatio(it, background) >= SharedSliderMinAccentContrast }
        ?: content
    val inactiveAlpha = if (background.luminance() > 0.5f) 0.44f else 0.52f
    return SharedReaderSliderChromeColors(
        activeTrackColor = active,
        inactiveTrackColor = content.copy(alpha = inactiveAlpha),
        thumbColor = active,
        contentColor = content,
    )
}

internal fun sharedSliderHighContrastColorFor(background: Color): Color {
    return if (
        sharedSliderContrastRatio(Color.Black, background) >=
        sharedSliderContrastRatio(Color.White, background)
    ) {
        Color.Black
    } else {
        Color.White
    }
}

internal fun sharedSliderContrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    val lighter = max(firstLuminance, secondLuminance)
    val darker = min(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}
