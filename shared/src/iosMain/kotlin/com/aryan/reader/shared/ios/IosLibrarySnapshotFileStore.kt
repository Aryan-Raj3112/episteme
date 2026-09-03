@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.aryan.reader.shared.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Durable mirror of the library snapshot, following Android's
 * `AtomicJsonFile` protocol (write `.new`, rename over target, keep `.bak`
 * until the new write lands, restore from `.bak` when a write fails).
 *
 * NSUserDefaults remains the primary fast store; this file is the recovery
 * path when the defaults entry is lost or corrupt, and the two are compared by
 * modification time so the newer payload always wins. Each write is atomic
 * (Foundation `writeToFile(atomically:)`), so a crash mid-write can never
 * leave a half-written file at the target path.
 */
internal object IosLibrarySnapshotFileStore {

    private const val DirectoryName = "LibraryState"
    private const val FileName = "library_snapshot_v1.json"
    private const val BackupSuffix = ".bak"

    /** Writes [json] atomically with the `.bak` protocol. Returns true on success. */
    fun write(json: String): Boolean {
        val path = filePath() ?: return false
        val fileManager = NSFileManager.defaultManager
        fileManager.createDirectoryAtPath(
            path = directoryPath() ?: return false,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val backupPath = path + BackupSuffix
        if (fileManager.fileExistsAtPath(path)) {
            // Promote the current payload to .bak before overwriting it. A stale
            // .bak from a previous cycle is removed first, mirroring Android.
            if (fileManager.fileExistsAtPath(backupPath)) {
                fileManager.removeItemAtPath(backupPath, error = null)
            }
            fileManager.copyItemAtPath(path, toPath = backupPath, error = null)
        }
        val written = json.encodeToByteArray().toNSData().writeToFile(path, atomically = true)
        if (written) {
            fileManager.removeItemAtPath(backupPath, error = null)
        }
        return written
    }

    /**
     * Reads the newest valid payload from the live file and its `.bak`. Either
     * side failing to decode is skipped; [decode] returning null means corrupt.
     */
    fun readNewest(decode: (String) -> Any?): Any? {
        val path = filePath() ?: return null
        val live = readValid(path, decode)
        val backup = readValid(path + BackupSuffix, decode)
        return when {
            live != null && backup != null -> {
                if (modificationMillis(path) >= modificationMillis(path + BackupSuffix)) live else backup
            }
            live != null -> live
            else -> backup
        }
    }

    private fun readValid(path: String, decode: (String) -> Any?): Any? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return null
        return decode(text)
    }

    private fun modificationMillis(path: String): Long {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0
        val date = attributes[NSFileModificationDate] as? NSDate ?: return 0
        return (date.timeIntervalSince1970 * 1000).toLong()
    }

    private fun directoryPath(): String? {
        return (NSFileManager.defaultManager.URLsForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomains = NSUserDomainMask
        ).firstOrNull() as? NSURL)?.path?.let { "$it/$DirectoryName" }
    }

    private fun filePath(): String? = directoryPath()?.let { "$it/$FileName" }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    val data = NSMutableData.dataWithLength(size.toULong()) ?: return NSData()
    usePinned { pinned ->
        memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
    }
    return data
}
