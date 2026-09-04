package com.aryan.reader.shared.reader

import kotlin.math.roundToLong
import kotlin.time.TimeSource

internal const val SharedEpubOpenTraceTag = "ReaderEpubOpenTrace"

private val SharedEpubOpenTraceOrigin = TimeSource.Monotonic.markNow()

internal fun sharedEpubOpenTraceNowMs(): Long =
    SharedEpubOpenTraceOrigin.elapsedNow().inWholeMilliseconds

internal fun sharedEpubOpenTraceMark(): TimeSource.Monotonic.ValueTimeMark =
    TimeSource.Monotonic.markNow()

internal fun sharedEpubOpenTraceElapsedMs(mark: TimeSource.Monotonic.ValueTimeMark): Double =
    mark.elapsedNow().inWholeMicroseconds / 1000.0

internal fun sharedEpubOpenTraceMs(ms: Double): String =
    "${(ms * 10.0).roundToLong() / 10.0}"

internal expect fun isSharedEpubOpenTraceEnabled(): Boolean

internal expect fun writeSharedEpubOpenTrace(message: String)

internal inline fun sharedEpubOpenTrace(message: () -> String) {
    if (isSharedEpubOpenTraceEnabled()) {
        writeSharedEpubOpenTrace(message())
    }
}
