package com.aryan.reader.pdf

import timber.log.Timber
import kotlin.math.roundToInt

internal object PdfVerticalPerfLog {
    const val TAG = "PdfVerticalPerf"
    const val SAMPLE_INTERVAL_MS = 250L

    fun nowNanos(): Long = System.nanoTime()

    fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    fun d(message: String) {
        Timber.tag(TAG).d(message)
    }

    fun i(message: String) {
        Timber.tag(TAG).i(message)
    }

    fun w(message: String) {
        Timber.tag(TAG).w(message)
    }

    fun f(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        return (value * 10f).roundToInt().let { rounded ->
            if (rounded % 10 == 0) (rounded / 10).toString()
            else (rounded / 10f).toString()
        }
    }

    fun xy(x: Float, y: Float): String = "(${f(x)},${f(y)})"
}
