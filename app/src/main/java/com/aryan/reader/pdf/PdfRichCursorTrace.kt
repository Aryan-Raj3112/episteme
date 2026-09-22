// PdfRichCursorTrace.kt
package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import timber.log.Timber

/**
 * Single log tag for page rich-text cursor/scroll diagnostics and the
 * follow-up list/alignment issues. Filter with:
 * adb logcat | grep -E "PdfRichCursor|FATAL EXCEPTION"
 */
internal const val PDF_RICH_CURSOR_TRACE_TAG = "PdfRichCursor"

internal fun pdfRichCursorTrace(message: String) {
    Timber.tag(PDF_RICH_CURSOR_TRACE_TAG).d(message)
}

internal fun Rect.pdfRichCursorSummary(): String {
    return "l=${left.pdfRichCursorFloat()} t=${top.pdfRichCursorFloat()} " +
        "r=${right.pdfRichCursorFloat()} b=${bottom.pdfRichCursorFloat()}"
}

private fun Float.pdfRichCursorFloat(): String {
    return if (isFinite()) {
        (kotlin.math.round(this * 10f) / 10f).toString()
    } else {
        toString()
    }
}

/**
 * Visible window the page cursor must stay inside while typing, in screen px.
 *
 * @param screenHeightPx reader viewport height.
 * @param imeBottomPx keyboard height (0 when closed).
 * @param dockReservePx text-toolbar height when it sits above the keyboard,
 *   0 when the bar is top-anchored or the keyboard is closed.
 * @param topSafePx keep-out below the top chrome.
 * @param caretPaddingPx breathing room around the caret so it never glues to
 *   the keyboard/toolbar edge.
 */
internal data class PdfCursorFollowWindow(
    val screenHeightPx: Float,
    val imeBottomPx: Float,
    val dockReservePx: Float,
    val topSafePx: Float,
    val caretPaddingPx: Float,
)

/**
 * Pan shift (screen px, negative scrolls content up) needed to keep the
 * cursor visible. 0 when already fully inside the window.
 *
 * Cursor doc coords are page-layout Y + cursor-rect offset in page bitmap px.
 */
internal fun pdfCursorFollowShift(
    cursorTopDocPx: Float,
    cursorBottomDocPx: Float,
    zoom: Float,
    panY: Float,
    window: PdfCursorFollowWindow,
): Float {
    val cursorTop = cursorTopDocPx * zoom + panY
    val cursorBottom = cursorBottomDocPx * zoom + panY
    val visibleBottom = window.screenHeightPx - window.imeBottomPx - window.dockReservePx
    return when {
        cursorBottom > visibleBottom - window.caretPaddingPx ->
            (visibleBottom - window.caretPaddingPx) - cursorBottom
        cursorTop < window.topSafePx + window.caretPaddingPx ->
            (window.topSafePx + window.caretPaddingPx) - cursorTop
        else -> 0f
    }
}
