@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.localFolderSyncSidecarStem
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * File-backed PDF sidecar storage for iOS.
 *
 * Reader state remains in NSUserDefaults for fast session restoration, while
 * this durable mirror is the transport boundary used by cloud/folder sync.
 * The file contains the shared [SharedPdfCloudSidecarCodec] envelope, so an
 * Android-produced annotation bundle can be read without an iOS-only format.
 */
object IosPdfCloudSidecarStore {
    private const val directoryName = "PdfSidecars"
    private const val fileSuffix = ".pdf-sidecar.json"

    /** Returns the stable local path that cloud integration should transport. */
    fun sidecarPath(bookId: String): String? {
        val root = applicationSupportDirectoryPath() ?: return null
        val directory = "$root/$directoryName"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return "$directory/${localFolderSyncSidecarStem(bookId)}$fileSuffix"
    }

    fun read(bookId: String): String? {
        val path = sidecarPath(bookId) ?: return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    fun readPayload(
        bookId: String,
        fallbackPageCount: Int = 1,
        fallbackPageIndex: Int = 0
    ): SharedPdfCloudSidecarPayload? {
        return SharedPdfCloudSidecarCodec.decode(
            rawDataJson = read(bookId),
            fallbackPageCount = fallbackPageCount,
            fallbackPageIndex = fallbackPageIndex
        )
    }

    /** Writes atomically so a cloud worker never observes a half-written JSON file. */
    fun write(bookId: String, payloadJson: String): Boolean {
        val path = sidecarPath(bookId) ?: return false
        return payloadJson.encodeToByteArray().toNSData().writeToFile(path, atomically = true)
    }

    fun writeState(
        bookId: String,
        state: SharedPdfReaderState,
        sourceFingerprint: String? = null,
        modifiedTimestamp: Long = 0L,
        existingDataJson: String? = read(bookId)
    ): Boolean {
        val payload = SharedPdfCloudSidecarCodec.encode(
            bookId = bookId,
            state = state,
            sourceFingerprint = sourceFingerprint,
            modifiedTimestamp = modifiedTimestamp,
            existingDataJson = existingDataJson
        )
        return write(bookId, payload)
    }

    fun delete(bookId: String): Boolean {
        val path = sidecarPath(bookId) ?: return false
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return true
        return NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun applicationSupportDirectoryPath(): String? {
        return (NSFileManager.defaultManager.URLsForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomains = NSUserDomainMask
        ).firstOrNull() as? NSURL)?.path
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSMutableData.dataWithLength(0u) ?: NSMutableData()
    val data = NSMutableData.dataWithLength(size.toULong()) ?: NSMutableData()
    usePinned { pinned ->
        memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
    }
    return data
}
