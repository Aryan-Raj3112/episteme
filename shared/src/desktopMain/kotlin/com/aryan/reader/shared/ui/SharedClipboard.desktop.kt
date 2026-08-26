package com.aryan.reader.shared.ui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Desktop fallback for shared dialogs that also run outside mobile. */
actual fun writeSharedClipboard(label: String, text: String): SharedClipboardResult {
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        SharedClipboardResult.success()
    } catch (_: Throwable) {
        SharedClipboardResult.failure(SharedClipboardFailureReason.WRITE_FAILED)
    }
}
