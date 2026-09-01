package com.aryan.reader.shared.reader

/**
 * Platform storage boundary for the shared measured-pagination page cache.
 *
 * Android is the behavioral benchmark; shared code owns the cache format and
 * eviction policy while each platform provides a real durable store.
 */
interface SharedEpubPageCacheStorage {
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun readBytes(path: String): ByteArray?
    fun writeBytesAtomically(path: String, bytes: ByteArray)
    fun deleteFile(path: String)
    fun deleteDirectory(path: String)
    fun deleteAll()
    fun listFileNames(path: String): List<String>
    fun lastModifiedMillis(path: String): Long
    fun rootLabel(): String
}

expect fun defaultSharedEpubPageCacheStorage(): SharedEpubPageCacheStorage

expect fun sharedSha256Hex(value: String): String
