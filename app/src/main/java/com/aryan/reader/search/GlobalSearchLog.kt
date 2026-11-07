package com.aryan.reader.search

import timber.log.Timber

/**
 * Diagnostic logging for the library-wide inside-files search pipeline.
 *
 * Timber only, and Timber is planted exclusively in debug builds
 * (`MyApplication.kt` gates the `DebugTree` behind `BuildConfig.DEBUG`), so
 * release builds emit nothing from these call sites. Filter with
 * `adb logcat -s GlobalSearch`.
 */
internal object GlobalSearchLog {
    const val TAG = "GlobalSearch"

    fun d(message: () -> String) {
        Timber.tag(TAG).d(message())
    }

    fun w(message: () -> String) {
        Timber.tag(TAG).w(message())
    }

    fun e(message: String, error: Throwable? = null) {
        Timber.tag(TAG).e(error, message)
    }
}

/** One-line summary for inline `cause=` logging. */
internal fun Throwable.brief(): String = "${this::class.java.simpleName}: ${message ?: "no message"}"
