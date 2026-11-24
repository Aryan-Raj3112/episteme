package com.aryan.reader.epubreader

import org.json.JSONObject
import org.json.JSONTokener

internal const val TAG_EPUB_VERTICAL_OPEN_DIAG = "EpubVerticalOpenDiag"

/**
 * How long a tab switch waits for the WebView to report its live CFI before falling back to the
 * in-memory flush. The round trip is normally single-digit milliseconds; this only bounds a
 * wedged renderer so switching a tab never hangs on it.
 */
internal const val CAPTURE_LIVE_POSITION_TIMEOUT_MS = 1_200L

/**
 * Pulls the position CFI out of a `window.getCurrentCfi()` result.
 *
 * `evaluateJavascript` returns the JS value encoded as JSON: either the object itself or a quoted
 * string containing it, and the JS helper answers `{"cfi": ...}`. Anything unparsable, empty, or
 * not CFI-shaped means "could not capture" and the caller falls back to the in-memory flush.
 */
internal fun readingPositionCfiFromJavascriptResult(raw: String?): String? {
    val trimmed = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val parsed = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
    val json = when (parsed) {
        is JSONObject -> parsed
        is String -> runCatching { JSONTokener(parsed).nextValue() }.getOrNull() as? JSONObject
        else -> null
    }
    return json?.optString("cfi")
        ?.takeIf { it.isNotBlank() && it.startsWith("/") }
}

internal const val VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS =
    com.aryan.reader.shared.reader.SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS

internal fun shouldSaveVerticalOpenPosition(
    isVerticalMode: Boolean,
    hasWebView: Boolean,
    isChapterReady: Boolean,
    isRestoreSettled: Boolean
): Boolean = com.aryan.reader.shared.reader.shouldSaveSharedVerticalOpenPosition(
    isVerticalMode = isVerticalMode,
    hasWebView = hasWebView,
    isChapterReady = isChapterReady,
    isRestoreSettled = isRestoreSettled,
)

internal fun resolveVerticalRestoreSettled(
    scrollFinishedReported: Boolean,
    restoreStartedAtMillis: Long,
    nowMillis: Long
): Boolean {
    if (scrollFinishedReported) return true
    if (restoreStartedAtMillis <= 0L) return true
    return com.aryan.reader.shared.reader.resolveSharedVerticalRestoreSettled(
        scrollFinishedReported = false,
        elapsedMillis = nowMillis - restoreStartedAtMillis,
        timeoutMillis = VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS,
    )
}
