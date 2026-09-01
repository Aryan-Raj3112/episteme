package com.aryan.reader.shared.ui

import platform.UIKit.UIPasteboard

/** iOS clipboard adapter backed by the system pasteboard. */
actual fun writeSharedClipboard(label: String, text: String): SharedClipboardResult {
    return try {
        UIPasteboard.generalPasteboard.string = text
        SharedClipboardResult.success()
    } catch (_: Throwable) {
        // UIPasteboard can throw when the app is not allowed to access the
        // pasteboard. Do not let that become a false-positive copy action.
        SharedClipboardResult.failure(SharedClipboardFailureReason.WRITE_FAILED)
    }
}
