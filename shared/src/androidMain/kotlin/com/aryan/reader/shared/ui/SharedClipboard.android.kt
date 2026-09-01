package com.aryan.reader.shared.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/** Android clipboard adapter retaining the existing SafeClipboard behavior. */
actual fun writeSharedClipboard(label: String, text: String): SharedClipboardResult {
    val context = sharedAndroidMobileApplicationContext()
        ?: return SharedClipboardResult.failure(SharedClipboardFailureReason.UNAVAILABLE)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return SharedClipboardResult.failure(SharedClipboardFailureReason.UNAVAILABLE)
    return try {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        SharedClipboardResult.success()
    } catch (error: SecurityException) {
        Log.w("SharedClipboard", "Clipboard write rejected by system policy", error)
        SharedClipboardResult.failure(SharedClipboardFailureReason.SECURITY_POLICY)
    } catch (error: RuntimeException) {
        Log.w("SharedClipboard", "Clipboard write failed", error)
        SharedClipboardResult.failure(SharedClipboardFailureReason.WRITE_FAILED)
    }
}
