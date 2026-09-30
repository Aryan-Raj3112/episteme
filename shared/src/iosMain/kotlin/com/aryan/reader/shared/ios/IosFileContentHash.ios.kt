@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.Sha256Stream
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Lowercase hex SHA-256 of a file's bytes, or null when it cannot be read.
 *
 * Android derives the book id from a content hash (`FileHasher.calculateSha256`
 * via `MessageDigest`) and that id becomes the CloudKit record name, which is
 * why the same book resolves to the same record on every device. iOS previously
 * fell back to a path-derived id, which is not content-addressed: a device that
 * imported a book and a device that downloaded it computed different ids, so a
 * single book became two records and two files on disk.
 *
 * Reads with the same `NSData.dataWithContentsOfFile` + `memcpy` pattern the rest
 * of this iOS codebase uses (IosAppFont, IosOpdsRepository), then feeds the bytes
 * to [Sha256Stream] in slices so the hash is computed incrementally and is
 * byte-identical to Android's `MessageDigest` output.
 */
internal fun iosFileContentSha256Hex(path: String): String? {
    val bytes = NSData.dataWithContentsOfFile(path)?.toWholeByteArray() ?: return null
    val stream = Sha256Stream()
    if (bytes.isEmpty()) return stream.digest().toLowerHex()
    try {
        var offset = 0
        while (offset < bytes.size) {
            val take = minOf(64 * 1024, bytes.size - offset)
            stream.update(bytes, offset, take)
            offset += take
        }
    } catch (_: Throwable) {
        return null
    }
    return stream.digest().toLowerHex()
}

private fun ByteArray.toLowerHex(): String = joinToString("") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

private fun NSData.toWholeByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
    return result
}
