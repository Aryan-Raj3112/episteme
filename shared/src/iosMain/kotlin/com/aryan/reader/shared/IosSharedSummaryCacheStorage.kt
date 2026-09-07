package com.aryan.reader.shared

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private const val IosSummaryCacheDir = "chapter_summaries"

actual fun defaultSharedSummaryCacheStorage(): SharedSummaryCacheStorage {
    return IosSharedSummaryCacheStorage.default()
}

@OptIn(ExperimentalForeignApi::class)
internal class IosSharedSummaryCacheStorage(
    private val rootPath: String
) : SharedSummaryCacheStorage {
    private val fileManager = NSFileManager.defaultManager

    override fun read(fileName: String): String? {
        return NSString.stringWithContentsOfFile(
            "$rootPath/$fileName",
            encoding = NSUTF8StringEncoding,
            error = null
        ) as String?
    }

    override fun write(fileName: String, content: String): Boolean {
        fileManager.createDirectoryAtPath(
            rootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return (content as NSString).writeToFile(
            "$rootPath/$fileName",
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
    }

    override fun delete(fileName: String): Boolean {
        val path = "$rootPath/$fileName"
        if (fileManager.fileExistsAtPath(path)) {
            return fileManager.removeItemAtPath(path, error = null)
        }
        return true
    }

    override fun listFileNames(): List<String> {
        return fileManager.contentsOfDirectoryAtPath(rootPath, error = null)
            ?.mapNotNull { it as? String }
            ?.filter { it.endsWith(".txt") }
            .orEmpty()
    }

    companion object {
        fun default(): IosSharedSummaryCacheStorage {
            val cachesDir = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            ).firstOrNull() as? String
            val root = if (cachesDir.isNullOrBlank()) {
                IosSummaryCacheDir
            } else {
                "$cachesDir/$IosSummaryCacheDir"
            }
            NSFileManager.defaultManager.createDirectoryAtPath(
                root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            return IosSharedSummaryCacheStorage(root)
        }
    }
}
