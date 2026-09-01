package com.aryan.reader

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Serializes destructive cloud-book work with a full sync/clear for each
 * account. Different accounts remain independent, while a clear-all cannot
 * race a delete worker and recreate or remove the wrong remote state.
 */
internal object CloudBookSyncBarrier {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withAccountLock(accountId: String, block: suspend () -> T): T {
        val normalized = accountId.trim()
        require(normalized.isNotBlank()) { "Cloud sync requires an account id" }
        val mutex = locks.getOrPut(normalized) { Mutex() }
        mutex.lock()
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
