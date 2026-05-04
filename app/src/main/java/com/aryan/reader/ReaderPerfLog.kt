package com.aryan.reader

import timber.log.Timber

object ReaderPerfLog {
    const val TAG = "ReaderPerf"

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

    inline fun <T> measure(
        name: String,
        minLogMs: Long = 16L,
        details: () -> String = { "" },
        block: () -> T
    ): T {
        val start = nowNanos()
        try {
            return block()
        } finally {
            val elapsed = elapsedMs(start)
            if (elapsed >= minLogMs) {
                val extra = details().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                d("$name took ${elapsed}ms$extra")
            }
        }
    }

    suspend inline fun <T> measureSuspend(
        name: String,
        minLogMs: Long = 16L,
        details: () -> String = { "" },
        crossinline block: suspend () -> T
    ): T {
        val start = nowNanos()
        try {
            return block()
        } finally {
            val elapsed = elapsedMs(start)
            if (elapsed >= minLogMs) {
                val extra = details().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                d("$name took ${elapsed}ms$extra")
            }
        }
    }
}
