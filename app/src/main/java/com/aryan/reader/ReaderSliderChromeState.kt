package com.aryan.reader

import android.content.Context
import androidx.core.content.edit

private const val READER_SLIDER_CHROME_PREFS = "reader_slider_chrome_prefs"
private const val READER_SLIDER_TOGGLE_PREFIX = "reader_slider_toggle_"

internal data class ReaderSliderBookmarkPosition(
    val startPage: Int,
    val currentPage: Float
)

internal data class ReaderSliderToggleState(
    val isToggledOn: Boolean,
    val bookmarkPosition: ReaderSliderBookmarkPosition
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
