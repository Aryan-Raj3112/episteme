package com.aryan.reader.shared

/**
 * Reader-only motion decisions shared by the native and WebView readers.
 *
 * The policy is intentionally small: a system setting can turn transitions
 * into an immediate state change, but it must not alter reading or formatting
 * semantics. User-facing animation preferences still decide normal behavior.
 */
data class ReaderMotionPolicy(
    val reduceMotion: Boolean = false,
) {
    val animationsEnabled: Boolean
        get() = !reduceMotion

    fun shouldAnimate(requested: Boolean = true): Boolean = requested && animationsEnabled

    fun durationMillis(normalDurationMillis: Int): Int =
        if (reduceMotion) 0 else normalDurationMillis

    fun webViewScrollBehavior(requestedSmooth: Boolean = true): String =
        if (requestedSmooth && animationsEnabled) "smooth" else "auto"
}
