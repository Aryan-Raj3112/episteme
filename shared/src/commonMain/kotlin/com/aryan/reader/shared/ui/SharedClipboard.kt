package com.aryan.reader.shared.ui

/**
 * Result of a platform clipboard write.
 *
 * Clipboard APIs are allowed to reject writes (for example, because the
 * system denies access while an app is backgrounded). Keeping the outcome in
 * shared code prevents callers from treating a best-effort write as an
 * unconditional success.
 */
data class SharedClipboardResult(
    val success: Boolean,
    val failureReason: SharedClipboardFailureReason? = null,
) {
    val succeeded: Boolean
        get() = success

    companion object {
        fun success(): SharedClipboardResult = SharedClipboardResult(success = true)

        fun failure(reason: SharedClipboardFailureReason): SharedClipboardResult =
            SharedClipboardResult(success = false, failureReason = reason)
    }
}

enum class SharedClipboardFailureReason {
    UNAVAILABLE,
    SECURITY_POLICY,
    WRITE_FAILED,
}

/**
 * Writes plain text to the platform clipboard and reports whether the write
 * was accepted. Implementations must catch platform clipboard exceptions.
 */
expect fun writeSharedClipboard(label: String, text: String): SharedClipboardResult
