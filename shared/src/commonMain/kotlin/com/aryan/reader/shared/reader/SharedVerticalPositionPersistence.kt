package com.aryan.reader.shared.reader

/** Android-benchmark budget for a virtualized vertical restore scroll before it is assumed dead. */
const val SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS = 6_000L

/**
 * Android-benchmark policy for deciding when a vertically scrolling EPUB surface may persist
 * its reading position.
 *
 * Chapter bodies are virtualized, so the restore scroll issued when the page finishes can keep
 * retrying for seconds while the host injects the chunks it needs to resolve the CFI. Persisting
 * before that scroll settles records the pre-restore viewport instead of the saved one, which
 * silently rewinds the stored position on every open. The surface therefore stays unsaveable
 * until the restore reports back through its scroll-finished signal.
 */
fun shouldSaveSharedVerticalOpenPosition(
    isVerticalMode: Boolean,
    hasWebView: Boolean,
    isChapterReady: Boolean,
    isRestoreSettled: Boolean,
): Boolean = isVerticalMode && hasWebView && isChapterReady && isRestoreSettled

/**
 * Resolves the settle state of a pending vertical restore scroll.
 *
 * The scroll-finished signal is authoritative, but it never arrives when the WebView is torn down
 * or the page fails to load, so an elapsed-time backstop is required: a restore that never reports
 * back must not disable position saving for the remainder of the session.
 */
fun resolveSharedVerticalRestoreSettled(
    scrollFinishedReported: Boolean,
    elapsedMillis: Long,
    timeoutMillis: Long = SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS,
): Boolean = scrollFinishedReported || elapsedMillis >= timeoutMillis
