package com.aryan.reader.shared.reader

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSMutableData
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.math.roundToLong

private const val IosBookLoadCacheDir = "episteme_book_load_cache"
private const val IosBookLoadCacheMaxEntries = 80

@OptIn(ExperimentalForeignApi::class)
internal class IosSharedBookLoadCacheStorage(
    private val rootPath: String
) : SharedBookLoadCacheStorage {

    private val fileManager = NSFileManager.defaultManager

    override fun read(cacheId: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(cacheFile(cacheId)) ?: return null
        return data.toByteArray()
    }

    override fun write(cacheId: String, bytes: ByteArray): Boolean {
        val file = cacheFile(cacheId)
        fileManager.createDirectoryAtPath(
            rootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return bytes.toNSData().writeToFile(file, atomically = true)
    }

    override fun cleanupOldEntries() {
        val files = fileManager.contentsOfDirectoryAtPath(rootPath, error = null)
            ?.mapNotNull { it as? String }
            ?.filter { it.endsWith(".book.pb") }
            ?.mapNotNull { name ->
                val path = "$rootPath/$name"
                val attributes = fileManager.attributesOfItemAtPath(path, error = null)
                val date = attributes?.get(NSFileModificationDate) as? NSDate
                (date?.timeIntervalSince1970?.let { it * 1000.0 } ?: 0.0).roundToLong() to path
            }
            ?.sortedByDescending { it.first }
            .orEmpty()
        files.drop(IosBookLoadCacheMaxEntries).forEach { (_, path) ->
            fileManager.removeItemAtPath(path, error = null)
        }
    }

    override fun clear() {
        fileManager.removeItemAtPath(rootPath, error = null)
        fileManager.createDirectoryAtPath(
            rootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    private fun cacheFile(cacheId: String): String {
        return "$rootPath/$cacheId.book.pb"
    }

    companion object {
        fun default(): IosSharedBookLoadCacheStorage {
            val cachesDir = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            ).firstOrNull() as? String
            val root = if (cachesDir.isNullOrBlank()) {
                IosBookLoadCacheDir
            } else {
                "$cachesDir/$IosBookLoadCacheDir"
            }
            NSFileManager.defaultManager.createDirectoryAtPath(
                root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            return IosSharedBookLoadCacheStorage(root)
        }
    }
}

actual fun defaultSharedBookLoadCacheStorage(): SharedBookLoadCacheStorage {
    return IosSharedBookLoadCacheStorage.default()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    val data = NSMutableData.dataWithLength(size.toULong()) ?: NSMutableData()
    val pinned = pin()
    return try {
        memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
        data
    } finally {
        pinned.unpin()
    }
}