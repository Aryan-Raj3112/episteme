package com.aryan.reader

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.content.edit
import kotlin.math.max
import kotlin.math.min

private const val READER_SLIDER_CHROME_PREFS = "reader_slider_chrome_prefs"
private const val READER_SLIDER_TOGGLE_PREFIX = "reader_slider_toggle_"
private const val MIN_SLIDER_ACCENT_CONTRAST = 3f
private const val MIN_SLIDER_CONTENT_CONTRAST = 4.5f

internal data class ReaderSliderBookmarkPosition(
    val startPage: Int,
    val currentPage: Float
)

internal data class ReaderSliderToggleState(
    val isToggledOn: Boolean,
    val bookmarkPosition: ReaderSliderBookmarkPosition
)

internal data class ReaderSliderChromeColors(
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
    val thumbColor: Color,
    val bookmarkColor: Color,
    val contentColor: Color,
    val thumbnailSurfaceColor: Color,
    val thumbnailContentColor: Color
)

internal fun readerSliderBookmarkPosition(currentPage: Int): ReaderSliderBookmarkPosition {
    val sanitizedPage = currentPage.coerceAtLeast(0)
    return ReaderSliderBookmarkPosition(
        startPage = sanitizedPage,
        currentPage = sanitizedPage.toFloat()
    )
}

internal fun readerSliderToggleState(
    isCurrentlyToggledOn: Boolean,
    currentPage: Int
): ReaderSliderToggleState {
    return ReaderSliderToggleState(
        isToggledOn = !isCurrentlyToggledOn,
        bookmarkPosition = readerSliderBookmarkPosition(currentPage)
    )
}

internal fun shouldRenderReaderSlider(
    isToggledOn: Boolean,
    isBottomChromeVisible: Boolean,
    isSearchActive: Boolean
): Boolean = isToggledOn && isBottomChromeVisible && !isSearchActive

internal fun readerSliderStepPage(
    currentPage: Int,
    delta: Int,
    minPage: Int,
    maxPage: Int
): Int {
    val lowerBound = min(minPage, maxPage)
    val upperBound = max(minPage, maxPage)
    val nextPage = currentPage.toLong() + delta.toLong()

    return nextPage
        .coerceIn(lowerBound.toLong(), upperBound.toLong())
        .toInt()
}

internal fun readerSliderTogglePreferenceKey(bookId: String): String =
    READER_SLIDER_TOGGLE_PREFIX + bookId

internal fun loadReaderSliderToggled(context: Context, bookId: String): Boolean {
    return context
        .getSharedPreferences(READER_SLIDER_CHROME_PREFS, Context.MODE_PRIVATE)
        .getBoolean(readerSliderTogglePreferenceKey(bookId), false)
}

internal fun saveReaderSliderToggled(
    context: Context,
    bookId: String,
    isToggledOn: Boolean
) {
    context
        .getSharedPreferences(READER_SLIDER_CHROME_PREFS, Context.MODE_PRIVATE)
        .edit { putBoolean(readerSliderTogglePreferenceKey(bookId), isToggledOn) }
}

internal fun readerSliderChromeColors(
    pageBackground: Color,
    pageText: Color,
    themePrimary: Color
): ReaderSliderChromeColors {
    val background = specifiedColorOr(pageBackground, Color.White)
    val fallbackContent = highContrastColorFor(background)
    val content = specifiedColorOr(pageText, fallbackContent)
        .takeIf { contrastRatio(it, background) >= MIN_SLIDER_CONTENT_CONTRAST }
        ?: fallbackContent
    val active = specifiedColorOr(themePrimary, content)
        .takeIf { contrastRatio(it, background) >= MIN_SLIDER_ACCENT_CONTRAST }
        ?: content
    val inactiveAlpha = if (background.luminance() > 0.5f) 0.44f else 0.52f
    val thumbnailSurface = blendColors(
        foreground = content,
        background = background,
        alpha = if (background.luminance() > 0.5f) 0.08f else 0.12f
    ).copy(alpha = 0.96f)

    return ReaderSliderChromeColors(
        activeTrackColor = active,
        inactiveTrackColor = content.copy(alpha = inactiveAlpha),
        thumbColor = active,
        bookmarkColor = active,
        contentColor = content,
        thumbnailSurfaceColor = thumbnailSurface,
        thumbnailContentColor = content
    )
}

private fun specifiedColorOr(color: Color, fallback: Color): Color {
    return if (color == Color.Unspecified) fallback else color
}

private fun highContrastColorFor(background: Color): Color {
    return if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    val lighter = max(firstLuminance, secondLuminance)
    val darker = min(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun blendColors(foreground: Color, background: Color, alpha: Float): Color {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * clampedAlpha + background.red * (1f - clampedAlpha),
        green = foreground.green * clampedAlpha + background.green * (1f - clampedAlpha),
        blue = foreground.blue * clampedAlpha + background.blue * (1f - clampedAlpha),
        alpha = 1f
    )
}
