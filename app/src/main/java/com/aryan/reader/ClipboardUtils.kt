package com.aryan.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import timber.log.Timber

internal fun copyPlainTextToClipboard(
    context: Context,
    label: String,
    text: String
): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    return setPrimaryClipSafely {
        clipboard.setPrimaryClip(clip)
    }
}

internal inline fun setPrimaryClipSafely(setPrimaryClip: () -> Unit): Boolean {
    return try {
        setPrimaryClip()
        true
    } catch (e: SecurityException) {
        Timber.w(e, "Clipboard write rejected by system policy")
        false
    } catch (e: RuntimeException) {
        Timber.w(e, "Clipboard write failed")
        false
    }
}
