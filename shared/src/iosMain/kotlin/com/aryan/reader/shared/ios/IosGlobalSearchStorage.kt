@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.search.SharedGlobalSearchPersistedBookIndex
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * File-backed [SharedGlobalSearchStorage] for iOS, mirroring Android's
 * `GlobalSearchFileStorage`: one JSON sidecar per book under
 * `ApplicationSupport/GlobalSearchIndex/`. Writes are atomic (Foundation
 * `writeToFile(atomically:)` is tmp + rename, so a killed process never
 * leaves a half-written index); corrupt files read as missing and are
 * deleted so the next indexing pass rebuilds them.
 *
 * Filenames are `gsi_<fnv1a64(bookId)>.json`: book ids are stable but not
 * filesystem-safe (linked-folder refs contain `://`), and the bookId inside
 * the payload remains the source of truth.
 */
internal class IosGlobalSearchStorage(
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SharedGlobalSearchStorage {

    override fun saveBook(index: SharedGlobalSearchPersistedBookIndex) {
        val dir = directoryPath() ?: return
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val target = "$dir/${fileNameFor(index.bookId)}"
        val bytes = json.encodeToString(
            SharedGlobalSearchPersistedBookIndex.serializer(),
            index,
        ).encodeToByteArray()
        bytes.toNSData().writeToFile(target, atomically = true)
    }

    override fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex? {
        val dir = directoryPath() ?: return null
        val path = "$dir/${fileNameFor(bookId)}"
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            ?: return null
        return try {
            json.decodeFromString(SharedGlobalSearchPersistedBookIndex.serializer(), text)
        } catch (_: Exception) {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            null
        }
    }

    override fun deleteBook(bookId: String) {
        val dir = directoryPath() ?: return
        NSFileManager.defaultManager.removeItemAtPath("$dir/${fileNameFor(bookId)}", error = null)
    }

    override fun storedBookIds(): Set<String> {
        val dir = directoryPath() ?: return emptySet()
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)
            as? List<String> ?: return emptySet()
        return names
            .filter { it.startsWith(FilePrefix) && it.endsWith(".json") }
            .mapNotNull { name ->
                val data = NSData.dataWithContentsOfFile("$dir/$name") ?: return@mapNotNull null
                val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                    ?: return@mapNotNull null
                runCatching {
                    json.decodeFromString(
                        SharedGlobalSearchPersistedBookIndex.serializer(),
                        text,
                    ).bookId
                }.getOrNull()
            }.toSet()
    }

    private fun directoryPath(): String? {
        val root = (NSFileManager.defaultManager.URLsForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomains = NSUserDomainMask,
        ).firstOrNull() as? platform.Foundation.NSURL)?.path ?: return null
        return "$root/$DirectoryName"
    }

    companion object {
        private const val DirectoryName = "GlobalSearchIndex"
        private const val FilePrefix = "gsi_"

        internal fun fileNameFor(bookId: String): String =
            "$FilePrefix${fnv1aHex(bookId)}.json"

        /** FNV-1a 64, hex. Stable, filesystem-safe sidecar names. */
        private fun fnv1aHex(value: String): String {
            var hash = -3750763034362895579L // 0xcbf29ce484222325 as Long
            for (byte in value.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xFF)
                hash *= 1099511628211L
            }
            return hash.toULong().toString(16).padStart(16, '0')
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    val data = NSMutableData.dataWithLength(size.toULong()) ?: return NSData()
    usePinned { pinned ->
        memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
    }
    return data
}
