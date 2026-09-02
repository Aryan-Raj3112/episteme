package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ios.IosDiagnosticLogStore

internal actual fun isSharedEpubOpenTraceEnabled(): Boolean = true

internal actual fun writeSharedEpubOpenTrace(message: String) {
    IosDiagnosticLogStore.record(SharedEpubOpenTraceTag, message)
    println("[${SharedEpubOpenTraceTag}] $message")
}
