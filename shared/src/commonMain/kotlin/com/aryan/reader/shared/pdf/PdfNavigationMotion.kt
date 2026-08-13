package com.aryan.reader.shared.pdf

import androidx.compose.ui.input.key.Key
import com.aryan.reader.shared.PdfDisplayMode

enum class PdfNavigationReason {
    INITIAL,
    TABLE_OF_CONTENTS,
    SEARCH_RESULT,
    PAGE_SLIDER,
    INTERNAL_LINK,
    JUMP_HISTORY,
    TTS,
    PAGE_TURN
}

/** Mirrors Android PDF pagination: targeted utility jumps snap; spatial reading transitions animate. */
fun PdfNavigationReason.animatesPagination(): Boolean = when (this) {
    PdfNavigationReason.INITIAL,
    PdfNavigationReason.TABLE_OF_CONTENTS,
    PdfNavigationReason.SEARCH_RESULT,
    PdfNavigationReason.PAGE_SLIDER -> false

    PdfNavigationReason.INTERNAL_LINK,
    PdfNavigationReason.JUMP_HISTORY,
    PdfNavigationReason.TTS,
    PdfNavigationReason.PAGE_TURN -> true
}

const val PdfChromeMotionDurationMillis = 200
const val PdfVerticalNavigationDurationMillis = 500

/** Lazy-list offset that places a point within the destination page at the viewport center. */
fun centeredPdfPageScrollOffset(
    viewportHeightPx: Int,
    pageHeightPx: Int,
    pageFraction: Float = 0.5f
): Int = (pageHeightPx * pageFraction.coerceIn(0f, 1f) - viewportHeightPx / 2f).toInt()

enum class SharedPdfKeyboardNavigationAction {
    NEXT_PAGE,
    PREVIOUS_PAGE,
    FIRST_PAGE,
    LAST_PAGE,
    SCROLL_DOWN,
    SCROLL_UP,
    NONE
}

/** Maps hardware keys to PDF reader navigation, mirroring the shared EPUB reader chrome. */
fun sharedPdfKeyboardNavigationAction(
    key: Key,
    displayMode: PdfDisplayMode,
): SharedPdfKeyboardNavigationAction = when (key) {
    Key.PageDown -> SharedPdfKeyboardNavigationAction.NEXT_PAGE
    Key.PageUp -> SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE
    Key.MoveHome -> SharedPdfKeyboardNavigationAction.FIRST_PAGE
    Key.MoveEnd -> SharedPdfKeyboardNavigationAction.LAST_PAGE
    Key.DirectionDown -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
        SharedPdfKeyboardNavigationAction.SCROLL_DOWN
    } else {
        SharedPdfKeyboardNavigationAction.NEXT_PAGE
    }
    Key.DirectionUp -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
        SharedPdfKeyboardNavigationAction.SCROLL_UP
    } else {
        SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE
    }
    else -> SharedPdfKeyboardNavigationAction.NONE
}
