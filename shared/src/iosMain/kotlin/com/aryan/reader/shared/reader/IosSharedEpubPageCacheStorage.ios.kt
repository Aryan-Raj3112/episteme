package com.aryan.reader.shared.reader

import com.aryan.reader.shared.sha256
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
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

private const val IosEpubPageCacheDir = "episteme_epub_page_cache"

@OptIn(ExperimentalForeignApi::class)
class IosSharedEpubPageCacheStorage(
    private val rootPath: String
) : SharedEpubPageCacheStorage {

    private val fileManager = NSFileManager.defaultManager

    override fun exists(path: String): Boolean {
        return fileManager.fileExistsAtPath(resolve(path))
    }

    override fun isDirectory(path: String): Boolean {
        return memScoped {
            val isDirectory = alloc<BooleanVar>()
            fileManager.fileExistsAtPath(resolve(path), isDirectory = isDirectory.ptr)
            isDirectory.value
        }
    }

    override fun readBytes(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(resolve(path)) ?: return null
        return data.toByteArray()
    }

    override fun writeBytesAtomically(path: String, bytes: ByteArray) {
        ensureParentDirectory(path)
        bytes.toNSData().writeToFile(resolve(path), atomically = true)
    }

    override fun deleteFile(path: String) {
        fileManager.removeItemAtPath(resolve(path), error = null)
    }

    override fun deleteDirectory(path: String) {
        fileManager.removeItemAtPath(resolve(path), error = null)
    }

    override fun deleteAll() {
        fileManager.removeItemAtPath(rootPath, error = null)
        fileManager.createDirectoryAtPath(
            rootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    override fun listFileNames(path: String): List<String> {
        return fileManager.contentsOfDirectoryAtPath(resolve(path), error = null)
            ?.mapNotNull { it as? String }
            ?: emptyList()
    }

    override fun lastModifiedMillis(path: String): Long {
        val attributes = fileManager.attributesOfItemAtPath(resolve(path), error = null) ?: return 0L
        val date = attributes[NSFileModificationDate] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000.0).roundToLong()
    }

    override fun rootLabel(): String = rootPath

    private fun ensureParentDirectory(path: String) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isBlank()) return
        fileManager.createDirectoryAtPath(
            resolve(parent),
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    private fun resolve(path: String): String {
        if (path.isBlank()) return rootPath
        return "$rootPath/$path"
    }

    companion object {
        fun default(): IosSharedEpubPageCacheStorage {
            val cachesDir = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            ).firstOrNull() as? String
            val root = if (cachesDir.isNullOrBlank()) {
                IosEpubPageCacheDir
            } else {
                "$cachesDir/$IosEpubPageCacheDir"
            }
            NSFileManager.defaultManager.createDirectoryAtPath(
                root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            return IosSharedEpubPageCacheStorage(root)
        }
    }
}

actual fun defaultSharedEpubPageCacheStorage(): SharedEpubPageCacheStorage {
    return IosSharedEpubPageCacheStorage.default()
}

actual fun sharedSha256Hex(value: String): String {
    return sha256(value.encodeToByteArray()).joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
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