package com.aryan.reader.shared

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun currentTimestamp(): Long {
    return memScoped {
        val now = alloc<timeval>()
        gettimeofday(now.ptr, null)
        now.tv_sec * 1000L + now.tv_usec / 1000L
    }
}
