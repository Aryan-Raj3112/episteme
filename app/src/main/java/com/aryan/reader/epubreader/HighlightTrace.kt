package com.aryan.reader.epubreader

import timber.log.Timber

/**
 * One tag for every highlight diagnostic, on both platforms.
 *
 * The document, the anchor resolver and the native painters all report here, so a highlight can be
 * followed end to end — created in one surface, anchored, carried to another, painted — from a single
 * logcat filter. The document's own lines arrive here through `onConsoleMessage`, because it always
 * `console.log`s them whether or not a native bridge exists.
 */
const val HIGHLIGHT_TRACE_TAG = "HIGHLIGHT_TRACE"

/** So call sites read as logging rather than as string building. */
fun logHighlightTrace(message: String) {
    if (message.isNotBlank()) Timber.tag(HIGHLIGHT_TRACE_TAG).d(message)
}