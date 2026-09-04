package com.aryan.reader.shared.reader

import android.util.Log

internal actual fun isSharedEpubOpenTraceEnabled(): Boolean =
    Log.isLoggable(SharedEpubOpenTraceTag, Log.DEBUG)

internal actual fun writeSharedEpubOpenTrace(message: String) {
    Log.d(SharedEpubOpenTraceTag, message)
}
