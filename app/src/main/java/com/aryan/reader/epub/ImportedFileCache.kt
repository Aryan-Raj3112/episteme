package com.aryan.reader.epub

import android.content.Context
import java.io.File
import java.util.UUID

object ImportedFileCache {
    private const val ACTIVE_PREFIX = "imported_file_"
    private const val TEMP_PREFIX = "imported_file_tmp_"
    private val invalidSegmentChars = Regex("[^A-Za-z0-9._-]+")

    fun activeBookDirName(bookId: String): String {
        return "$ACTIVE_PREFIX${bookMarker(bookId)}"
    }

    fun activeBookDir(context: Context, bookId: String): File {
        return File(context.cacheDir, activeBookDirName(bookId))
    }

    fun prepareActiveBookDir(context: Context, bookId: String): File {
        return prepareDirectory(activeBookDir(context, bookId))
    }

    fun createTemporaryBookDir(context: Context, bookId: String, purpose: String): File {
        val dirName = buildString {
            append(TEMP_PREFIX)
            append(purpose.toCacheSegment())
            append('_')
            append(bookMarker(bookId))
            append('_')
            append(UUID.randomUUID())
        }
        return prepareDirectory(File(context.cacheDir, dirName))
    }

    fun prepareDirectory(directory: File): File {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        directory.mkdirs()
        return directory
    }

    fun clearBookCache(context: Context, bookId: String) {
        activeBookDir(context, bookId).takeIf { it.exists() }?.deleteRecursively()
        legacyActiveBookDir(context, bookId).takeIf { it.exists() }?.deleteRecursively()
        clearTemporaryBookDirs(context, bookId)
    }

    fun clearTemporaryBookDirs(context: Context, bookId: String) {
        val marker = "_${bookMarker(bookId)}_"
        context.cacheDir.listFiles()?.forEach { file ->
            if (isTemporaryBookDir(file.name) && file.name.contains(marker)) {
                file.deleteRecursively()
            }
        }
    }

    fun deleteStaleTemporaryBookDirs(
        context: Context,
        olderThanMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (isTemporaryBookDir(file.name) && nowMillis - file.lastModified() >= olderThanMillis) {
                file.deleteRecursively()
            }
        }
    }

    fun isTemporaryBookDir(name: String): Boolean = name.startsWith(TEMP_PREFIX)

    fun isActiveBookDir(name: String): Boolean {
        return name.startsWith(ACTIVE_PREFIX) && !isTemporaryBookDir(name)
    }

    private fun legacyActiveBookDir(context: Context, bookId: String): File {
        return File(context.cacheDir, "$ACTIVE_PREFIX$bookId")
    }

    private fun bookMarker(bookId: String): String {
        val normalized = bookId.toCacheSegment().ifBlank { "book" }.take(40)
        val hash = bookId.hashCode().toLong() and 0xffffffffL
        return "${normalized}_${hash.toString(16)}"
    }

    private fun String.toCacheSegment(): String {
        return replace(invalidSegmentChars, "_").trim('_')
    }
}
